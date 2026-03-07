package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.component.orchestration.ExecutableTask;
import com.example.lazarus_backend00.component.pool.ModelContainerPool;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.dto.TaskStatusDTO;
import com.example.lazarus_backend00.infrastructure.config.ModelPoolConfig;
import com.example.lazarus_backend00.service.DataPreloadService;
import com.example.lazarus_backend00.service.DataService;
import com.example.lazarus_backend00.service.ModelOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ModelOrchestratorServiceImpl implements ModelOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ModelOrchestratorServiceImpl.class);
    private final ModelPoolConfig poolConfig;
    private final DataPreloadService dataPreloadService;
    private final DataService dataService;
    private final ModelContainerPool containerPool;
    private final Object dataMemoryLock = new Object();

    private final Map<Long, TaskStatusDTO> activeTasksMap = new ConcurrentHashMap<>();

    public ModelOrchestratorServiceImpl(DataPreloadService dataPreloadService,
                                        DataService dataService,
                                        @Lazy ModelContainerPool containerPool,
                                        ModelPoolConfig poolConfig) {
        this.dataPreloadService = dataPreloadService;
        this.dataService = dataService;
        this.containerPool = containerPool;
        this.poolConfig = poolConfig;
    }

    @Override
    @Async("taskExecutor")
    public void dispatchTask(ExecutableTask task) {
        long taskId = task.getTaskId();
        int runtimeId = task.getContainerId();

        TaskStatusDTO statusTracker = new TaskStatusDTO(
                taskId, runtimeId, "PENDING", Instant.now(), "Waiting for memory admission..."
        );
        activeTasksMap.put(taskId, statusTracker);

        try {
            waitForSufficientMemory(taskId, statusTracker);

            statusTracker.setStatus("PREPARING_DATA");
            statusTracker.setMessage("Requesting tensor data from data subsystem...");

            List<TSShell> requiredShells = task.getAllInputShells();
            if (!requiredShells.isEmpty()) {
                dataPreloadService.startFetching(taskId, requiredShells);
            }
            List<TSDataBlock> flatInputData = requiredShells.isEmpty()
                    ? new ArrayList<>()
                    : dataPreloadService.getData(taskId, 60);

            List<List<TSDataBlock>> structuredInputs = assembleInputGroups(task, flatInputData);

            statusTracker.setStatus("COMPUTING");
            statusTracker.setMessage("Computing in container pool...");

            log.info("🧠 [Orch] Task-{} assembled {} input groups. Entering execution pool...", taskId, structuredInputs.size());

            // =========================================================================
            // 👇 就是这里！核心替换部分开始 👇
            // =========================================================================

            // 1. 模型计算
            List<List<TSDataBlock>> structuredResults = containerPool.executeModel(runtimeId, structuredInputs);

            // 确保模型确实返回了数据，没有内部崩溃
            if (structuredResults == null || structuredResults.isEmpty()) {
                log.warn("⚠️ [Orch] Task-{} execution returned NULL or EMPTY results! Model failed internally.", taskId);
            } else {
                // 2. 像素级过滤（根据 TSState 掩码，把大盘里已有的数据剔除）
                List<List<TSDataBlock>> finalFilteredResults = task.filterRedundantOutputs(structuredResults);

                // 3. 检查过滤后是否还有“有效产出”
                if (finalFilteredResults.isEmpty()) {
                    // 这种情况很正常：说明这批数据和历史观测数据100%重合，不需要这批仿真数据了
                    log.info("💤 [Orch] Task-{} results are redundant. All pixels already exist in global state.", taskId);
                } else {
                    // 只有真有新数据，才推给存储端
                    log.info("📦 [Orch] Task-{} filtered. Pushing {} remaining valid ports.", taskId, finalFilteredResults.size());
                    handleResults(task, finalFilteredResults);
                    log.info("✅ [Orch] Task-{} results successfully pushed to data subsystem.", taskId);
                }
            }
            // =========================================================================
            // 👆 核心替换部分结束 👆
            // =========================================================================

            activeTasksMap.remove(taskId);

        } catch (Exception e) {
            log.error("❌ [Orch] Task-{} execution failed: {}", taskId, e.getMessage());
            statusTracker.setStatus("ERROR");
            statusTracker.setMessage("Execution failed: " + e.getMessage());
        } finally {
            System.gc();
            synchronized (dataMemoryLock) {
                dataMemoryLock.notifyAll();
            }
        }
    }

    private void waitForSufficientMemory(long taskId, TaskStatusDTO statusTracker) throws InterruptedException {
        synchronized (dataMemoryLock) {
            while (true) {
                long maxMemory = Runtime.getRuntime().maxMemory();
                long totalMemory = Runtime.getRuntime().totalMemory();
                long freeMemory = Runtime.getRuntime().freeMemory();
                long actualFreeMB = ((maxMemory - totalMemory) + freeMemory) / (1024 * 1024);

                long thresholdMB = poolConfig.getMinFreeMemoryMb();

                if (actualFreeMB >= thresholdMB) {
                    log.info("🟢 [MemAdmission] Available memory {} MB >= threshold, Task-{} approved.", actualFreeMB, taskId);
                    break;
                }

                statusTracker.setMessage(String.format("Insufficient memory (Available %d MB), queuing...", actualFreeMB));
                log.warn("🟡 [MemIntercept] Available memory {} MB is insufficient! Task-{} waiting...", actualFreeMB, taskId);

                dataMemoryLock.wait(5000);
            }
        }
    }

    private void handleResults(ExecutableTask task, List<List<TSDataBlock>> structuredResults) {
        List<ExecutableTask.TaskPort> outputPorts = task.getOutputs();
        for (int i = 0; i < structuredResults.size(); i++) {
            if (i >= outputPorts.size()) break;

            List<TSDataBlock> featureBlocks = structuredResults.get(i);
            for (TSDataBlock resultBlock : featureBlocks) {
                dataService.pushData(resultBlock.getFeatureId(), resultBlock);
            }
        }
    }

    @Override
    public List<TaskStatusDTO> getActiveTasks() {
        return new ArrayList<>(activeTasksMap.values());
    }

    private List<List<TSDataBlock>> assembleInputGroups(ExecutableTask task, List<TSDataBlock> flatData) {
        Map<String, TSDataBlock> dataMap = flatData.stream()
                .collect(Collectors.toMap(
                        b -> b.getFeatureId() + "_" + (b.getTOrigin() != null ? b.getTOrigin().toEpochMilli() : "0"),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        List<List<TSDataBlock>> inputGroups = new ArrayList<>();

        for (ExecutableTask.TaskPort port : task.getInputs()) {
            List<TSDataBlock> portBlocks = new ArrayList<>();
            for (TSShell shell : port.getAtomicShells()) {

                String key = shell.getFeatureId() + "_" + (shell.getTOrigin() != null ? shell.getTOrigin().toEpochMilli() : "0");
                TSDataBlock block = dataMap.get(key);

                if (block == null) {
                    throw new IllegalStateException("Required input data missing: Feature " + shell.getFeatureId() + " (at time: " + shell.getTOrigin() + ")");
                }
                portBlocks.add(block);
            }
            inputGroups.add(portBlocks);
        }
        return inputGroups;
    }
}