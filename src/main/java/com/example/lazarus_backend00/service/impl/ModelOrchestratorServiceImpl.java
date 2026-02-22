package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.component.orchestration.ExecutableTask;
import com.example.lazarus_backend00.component.pool.ModelContainerPool;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ModelOrchestratorServiceImpl implements ModelOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ModelOrchestratorServiceImpl.class);

    private final DataPreloadService dataPreloadService;
    private final DataService dataService;
    private final ModelContainerPool containerPool;

    // 注意：不再需要直接依赖 EventTrigger，因为任务是由 DataService 传进来的
    public ModelOrchestratorServiceImpl(DataPreloadService dataPreloadService,
                                        DataService dataService,
                                        ModelContainerPool containerPool) {
        this.dataPreloadService = dataPreloadService;
        this.dataService = dataService;
        this.containerPool = containerPool;
    }

    // ========================================================================
    // 1. 任务分发与执行 (Dispatch & Execute)
    // ========================================================================

    @Override
    @Async("taskExecutor") // 异步执行，不阻塞 DataService
    public void dispatchTask(ExecutableTask task) {
        long taskId = task.getTaskId();
        int runtimeId = task.getContainerId();

        try {
            // A. [IO Path] 启动数据预取
            // 获取任务所需的所有原子 Shell
            List<TSShell> requiredShells = task.getAllInputShells();
            if (!requiredShells.isEmpty()) {
                dataPreloadService.startFetching(taskId, requiredShells);
            }

            // B. [Rendezvous] 汇合点：等待数据
            // DataPreloadService 内部会调用 DataService.fetchData (HTTP/Cache)
            // 设定超时时间 60秒
            List<TSDataBlock> flatInputData = requiredShells.isEmpty()
                    ? new ArrayList<>()
                    : dataPreloadService.getData(taskId, 60);

            // C. [Structure Assembly] 结构组装
            // 将扁平的 Blocks 组装成容器需要的 List<List<TSDataBlock>> (按张量分组)
            List<List<TSDataBlock>> structuredInputs = assembleInputGroups(task, flatInputData);

            // D. [Compute Path] 执行计算 (进入容器)
            // executeModel 内部处理了 LRU 和并发锁
            List<List<TSDataBlock>> structuredResults = containerPool.executeModel(runtimeId, structuredInputs);

            // E. [Result Handling] 结果处理
            if (structuredResults != null && !structuredResults.isEmpty()) {
                handleResults(task, structuredResults);
            }

        } catch (Exception e) {
            log.error("❌ [Orch] Task-{} (Model {}) 执行失败: {}", taskId, runtimeId, e.getMessage());
            // e.printStackTrace();
        }
    }

    @Override
    public void onDataChanged(int featureId, Instant start, Instant end) {
        // 此方法在当前架构中已弃用，逻辑移至 DataServiceImpl
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