package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.annotation.AuditAnnotations;
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
    // 🔥 新增：配置类实例
    private final ModelPoolConfig poolConfig;
    private final DataPreloadService dataPreloadService;
    private final DataService dataService;
    private final ModelContainerPool containerPool;
    private final Object dataMemoryLock = new Object();
    // 注意：不再需要直接依赖 EventTrigger，因为任务是由 DataService 传进来的
    private final Map<Long, TaskStatusDTO> activeTasksMap = new ConcurrentHashMap<>();
    public ModelOrchestratorServiceImpl(DataPreloadService dataPreloadService,
                                        DataService dataService,
                                        ModelContainerPool containerPool,
                                        ModelPoolConfig poolConfig) {
        this.dataPreloadService = dataPreloadService;
        this.dataService = dataService;
        this.containerPool = containerPool;
        this.poolConfig = poolConfig;
    }

    // ========================================================================
    // 1. 任务分发与执行 (Dispatch & Execute)
    // ========================================================================


    @Override
    @Async("taskExecutor")
    @AuditAnnotations.LogModelTriggered
    public void dispatchTask(ExecutableTask task) {
        long taskId = task.getTaskId();
        int runtimeId = task.getContainerId();

        // 🌟 1. 初始化任务状态，放入 PENDING
        TaskStatusDTO statusTracker = new TaskStatusDTO(
                taskId, runtimeId, "PENDING", Instant.now(), "等待内存准入..."
        );
        activeTasksMap.put(taskId, statusTracker);

        try {
            // 🌟 2. 内存准入检查
            waitForSufficientMemory(taskId, statusTracker);

            // 🌟 3. 更新状态：准备数据
            statusTracker.setStatus("PREPARING_DATA");
            statusTracker.setMessage("正在向数据系统索要张量数据...");

            List<TSShell> requiredShells = task.getAllInputShells();
            if (!requiredShells.isEmpty()) {
                dataPreloadService.startFetching(taskId, requiredShells);
            }
            List<TSDataBlock> flatInputData = requiredShells.isEmpty()
                    ? new ArrayList<>()
                    : dataPreloadService.getData(taskId, 60);

            List<List<TSDataBlock>> structuredInputs = assembleInputGroups(task, flatInputData);

            // 🌟 4. 更新状态：入池计算
            statusTracker.setStatus("COMPUTING");
            statusTracker.setMessage("正在容器池内进行计算...");

            List<List<TSDataBlock>> structuredResults = containerPool.executeModel(runtimeId, structuredInputs);

            if (structuredResults != null && !structuredResults.isEmpty()) {
                handleResults(task, structuredResults);
            }

            // 🌟 5. 成功完成，从活跃任务表中移除 (前端收到列表不含此任务即代表完成)
            activeTasksMap.remove(taskId);

        } catch (Exception e) {
            log.error("❌ [Orch] Task-{} 执行失败: {}", taskId, e.getMessage());
            // 发生异常时，保留在列表中展示给前端，状态置为 ERROR
            statusTracker.setStatus("ERROR");
            statusTracker.setMessage("执行失败: " + e.getMessage());
        } finally {
            System.gc();
            synchronized (dataMemoryLock) {
                dataMemoryLock.notifyAll();
            }
        }
    }

    /**
     * 检查系统内存，不足则挂起当前线程
     */
    private void waitForSufficientMemory(long taskId, TaskStatusDTO statusTracker) throws InterruptedException {
        synchronized (dataMemoryLock) {
            while (true) {
                long maxMemory = Runtime.getRuntime().maxMemory();
                long totalMemory = Runtime.getRuntime().totalMemory();
                long freeMemory = Runtime.getRuntime().freeMemory();
                long actualFreeMB = ((maxMemory - totalMemory) + freeMemory) / (1024 * 1024);

                long thresholdMB = poolConfig.getMinFreeMemoryMb();

                if (actualFreeMB >= thresholdMB) {
                    log.info("🟢 [内存准入] 可用内存 {} MB >= 阈值，Task-{} 获批", actualFreeMB, taskId);
                    break;
                }

                // 更新前端展示的排队信息
                statusTracker.setMessage(String.format("内存不足 (可用 %d MB)，排队中...", actualFreeMB));
                log.warn("🟡 [内存拦截] 可用内存 {} MB 不足！Task-{} 等待...", actualFreeMB, taskId);

                dataMemoryLock.wait(5000);
            }
        }
    }

    // ========================================================================
    // 2. 结果处理 (Result Handling)
    // ========================================================================

    private void handleResults(ExecutableTask task, List<List<TSDataBlock>> structuredResults) {
        List<ExecutableTask.TaskPort> outputPorts = task.getOutputs();

        // 1. 遍历输出端口
        for (int i = 0; i < structuredResults.size(); i++) {
            if (i >= outputPorts.size()) break;

            List<TSDataBlock> featureBlocks = structuredResults.get(i);

            // 2. 遍历端口内的每个 Block
            for (TSDataBlock resultBlock : featureBlocks) {
                // 🔥 核心：将结果推回 DataService
                // DataService 会负责：缓存 + 远程存盘 + 级联触发
                dataService.pushData(resultBlock.getFeatureId(), resultBlock);
            }
        }
        // log.info("✅ [Orch] Task-{} 完成。", task.getTaskId());
    }
    // 🔥 新增：暴露给 Controller 的方法
    @Override
    public List<TaskStatusDTO> getActiveTasks() {
        // 直接返回 Map 中的所有值
        return new ArrayList<>(activeTasksMap.values());
    }

    // ========================================================================
    // 3. 辅助方法 (Helpers)
    // ========================================================================

    private List<List<TSDataBlock>> assembleInputGroups(ExecutableTask task, List<TSDataBlock> flatData) {
        // 建立索引: FeatureID -> Block
        Map<Integer, TSDataBlock> dataMap = flatData.stream()
                .collect(Collectors.toMap(TSDataBlock::getFeatureId, Function.identity(), (e, r) -> e));

        List<List<TSDataBlock>> inputGroups = new ArrayList<>();

        for (ExecutableTask.TaskPort port : task.getInputs()) {
            List<TSDataBlock> portBlocks = new ArrayList<>();
            for (TSShell shell : port.getAtomicShells()) {
                TSDataBlock block = dataMap.get(shell.getFeatureId());
                if (block == null) {
                    throw new IllegalStateException("缺少必要输入数据: Feature " + shell.getFeatureId());
                }
                portBlocks.add(block);
            }
            inputGroups.add(portBlocks);
        }
        return inputGroups;
    }
}