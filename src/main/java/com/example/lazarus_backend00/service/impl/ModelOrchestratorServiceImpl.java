package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.component.orchestration.ExecutableTask;
import com.example.lazarus_backend00.component.pool.ModelContainerPool;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.domain.data.TSState;
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

        // =========================================================================
        // 🎯 新增日志：打印任务的实际输入和预期输出时间段 (清爽聚合版)
        // =========================================================================
        StringBuilder timeLog = new StringBuilder("\n=== Task-" + taskId + " Time Ranges ===\n▶️ Inputs:\n");
        for (ExecutableTask.TaskPort port : task.getInputs()) {
            if (!port.getAtomicShells().isEmpty()) {
                timeLog.append("   - Port ").append(port.getOrder()).append(": ")
                        .append(formatTimeRange(port.getAtomicShells().get(0))).append("\n");
            }
        }
        timeLog.append("▶️ Expected Outputs:\n");
        for (ExecutableTask.TaskPort port : task.getOutputs()) {
            for (List<TSState> featureStates : port.getTargetStatesPerFeature()) {
                if (!featureStates.isEmpty()) {
                    TSState firstState = featureStates.get(0);
                    TSState lastState = featureStates.get(featureStates.size() - 1);

                    String startTime = formatTimeRange(firstState).split(" to ")[0].replace("[", "");
                    String endTime = formatTimeRange(lastState).split(" to ")[1].replace("]", "");

                    timeLog.append("   - Feature ").append(firstState.getFeatureId()).append(": [")
                            .append(startTime).append(" to ").append(endTime).append("]\n");
                }
            }
        }
        timeLog.append("===================================");
        log.info(timeLog.toString());
        // =========================================================================

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

            log.info("[Orch] Task-{} assembled {} input groups. Entering execution pool...", taskId, structuredInputs.size());

            // =========================================================================
            // 🕵️ [致命追踪 - 输入侧] 检查交给模型的输入数据是否自带 NaN！
            // =========================================================================
            boolean hasInputNaN = false;
            for (int pIdx = 0; pIdx < structuredInputs.size(); pIdx++) {
                List<TSDataBlock> portBlocks = structuredInputs.get(pIdx);
                for (TSDataBlock block : portBlocks) {
                    float[] data = block.getData();
                    int nanCount = 0;
                    if (data != null) {
                        for (float v : data) {
                            if (Float.isNaN(v)) nanCount++;
                        }
                    }
                    if (nanCount > 0) {
                        hasInputNaN = true;
                        System.err.println("\n❌ [致命追踪 - 输入侧] Task-" + taskId + " 查出源头真凶！");
                        System.err.println("❌ 真相大白：喂给模型的输入数据本身就带毒！输入端口 " + pIdx + " (特征 " + block.getFeatureId() + ") 包含了 " + nanCount + " 个 NaN！");
                        System.err.println("❌ 结论：这是【数据获取端/补齐逻辑】的问题！请检查数据库里的 .tif 是否有空洞未插值，或者缺失的数据被默认赋成了 NaN！\n");
                    }
                }
            }

            if (!hasInputNaN) {
                System.out.println("✅ [致命追踪 - 输入侧] Task-" + taskId + " 输入数据体检通过，全部都是合法数字，没有任何 NaN。");
                System.out.println("✅ 结论：如果最终输出变成了 NaN，那绝对是【ONNX 模型本身】算崩溃了（如除以 0 或权重损坏）！");
            }
            // =========================================================================


            // 1. 模型计算
            List<List<TSDataBlock>> structuredResults = containerPool.executeModel(runtimeId, structuredInputs);

            // 确保模型确实返回了数据，没有内部崩溃
            if (structuredResults == null || structuredResults.isEmpty()) {
                log.warn("[Orch] Task-{} execution returned NULL or EMPTY results! Model failed internally.", taskId);
            } else {

                // 2. 像素级过滤（根据 TSState 掩码，把大盘里已有的数据剔除）
                List<List<TSDataBlock>> finalFilteredResults = task.filterRedundantOutputs(structuredResults);

                // 3. 检查过滤后是否还有“有效产出”
                if (finalFilteredResults.isEmpty()) {
                    // 这种情况很正常：说明这批数据和历史观测数据100%重合，不需要这批仿真数据了
                    log.info("[Orch] Task-{} results are redundant. All pixels already exist in global state.", taskId);
                } else {
                    // 只有真有新数据，才推给存储端
                    log.info("[Orch] Task-{} filtered. Pushing {} remaining valid ports.", taskId, finalFilteredResults.size());
                    handleResults(task, finalFilteredResults);
                    log.info("[Orch] Task-{} results successfully pushed to data subsystem.", taskId);
                }
            }

            activeTasksMap.remove(taskId);

        } catch (Exception e) {
            log.error("[Orch] Task-{} execution failed: {}", taskId, e.getMessage());
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

    // =========================================================================
    // 🛠️ 新增工具方法：根据 TSShell 动态推算并打印时间段 (自带单位换算)
    // =========================================================================
    private String formatTimeRange(TSShell shell) {
        if (shell == null || shell.getTOrigin() == null) return "N/A";
        if (shell.getTAxis() == null || shell.getTAxis().getCount() == null) return "[" + shell.getTOrigin().toString() + "]";

        double res = shell.getTAxis().getResolution() != null ? shell.getTAxis().getResolution() : 1.0;
        int count = shell.getTAxis().getCount();
        String unit = shell.getTAxis().getUnit() != null ? shell.getTAxis().getUnit().trim().toLowerCase() : "s";

        long multiplier = 1;
        if (unit.startsWith("h")) multiplier = 3600;
        else if (unit.startsWith("m")) multiplier = 60;
        else if (unit.startsWith("d")) multiplier = 86400;

        long totalSeconds = (long) (res * count * multiplier);
        return "[" + shell.getTOrigin().toString() + " to " + shell.getTOrigin().plusSeconds(totalSeconds).toString() + "]";
    }

}