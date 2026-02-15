//package com.example.lazarus_backend00.service.impl;
//
//import com.example.lazarus_backend00.component.orchestration.ExecutableTask;
//import com.example.lazarus_backend00.component.orchestration.ModelEventTrigger;
//import com.example.lazarus_backend00.component.pool.ModelContainerPool;
//import com.example.lazarus_backend00.domain.axis.TimeAxis;
//import com.example.lazarus_backend00.domain.data.TSDataBlock;
//import com.example.lazarus_backend00.domain.data.TSShell;
//import com.example.lazarus_backend00.service.DataPreloadService;
//import com.example.lazarus_backend00.service.DataService;
//import com.example.lazarus_backend00.service.ModelOrchestratorService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Service;
//
//import java.time.Instant;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.function.Function;
//import java.util.stream.Collectors;
//
//@Service
//public class ModelOrchestratorServiceImpl implements ModelOrchestratorService {
//
//    private static final Logger log = LoggerFactory.getLogger(ModelOrchestratorServiceImpl.class);
//
//    private final DataPreloadService dataPreloadService;
//    private final DataService dataService;
//    private final ModelContainerPool containerPool;
//    private final ModelEventTrigger eventTrigger;
//
//    public ModelOrchestratorServiceImpl(DataPreloadService dataPreloadService,
//                                        DataService dataService,
//                                        ModelContainerPool containerPool,
//                                        ModelEventTrigger eventTrigger) {
//        this.dataPreloadService = dataPreloadService;
//        this.dataService = dataService;
//        this.containerPool = containerPool;
//        this.eventTrigger = eventTrigger;
//    }
//
//    // ========================================================================
//    // 1. 信号处理 (Signal Handling)
//    // ========================================================================
//
//    @Override
//    @Async("taskExecutor")
//    public void onDataChanged(int featureId, Instant start, Instant end) {
//        try {
//            // 1. 计算总时长 (秒)
//            long durationSeconds = end.getEpochSecond() - start.getEpochSecond();
//            if (durationSeconds <= 0) durationSeconds = 1;
//
//            // 2. 构建 TimeAxis (使用你提供的4参数构造函数)
//            // 逻辑：Range == Resolution 表示 Count=1，即这是一个整体的时间包
//            TimeAxis notificationAxis = new TimeAxis(
//                    (double) durationSeconds, "seconds", // range
//                    (double) durationSeconds, "seconds"  // resolution
//            );
//
//            // 3. 构建变更通知 Shell
//            TSShell updateShell = new TSShell.Builder(featureId)
//                    .time(start, notificationAxis)
//                    .build();
//
//            // 4. 询问触发器 (UDCR 算法)
//            List<ExecutableTask> tasks = eventTrigger.onDataUpdate(updateShell);
//
//            if (tasks.isEmpty()) return;
//
//            log.info("📡 [Orch] 数据 F{} 更新，触发 {} 个计算任务", featureId, tasks.size());
//
//            // 5. 分发任务
//            for (ExecutableTask task : tasks) {
//                dispatchTask(task);
//            }
//
//        } catch (Exception e) {
//            log.error("💥 [Orch] 信号处理异常: {}", e.getMessage(), e);
//        }
//    }
//
//    // ========================================================================
//    // 2. 任务分发与执行 (Dispatch & Execute)
//    // ========================================================================
//
//    @Override
//    @Async("taskExecutor")
//    public void dispatchTask(ExecutableTask task) {
//        long taskId = task.getTaskId();
//        int runtimeId = task.getContainerId();
//
//        try {
//            // A. [IO Path] 启动数据预取
//            // 获取任务所需的所有原子 Shell (扁平化列表)
//            List<TSShell> requiredShells = task.getAllInputShells();
//            dataPreloadService.startFetching(taskId, requiredShells);
//
//            // B. [Rendezvous] 汇合点：等待数据
//            // DataPreloadService 返回的是扁平的 List<TSDataBlock>
//            List<TSDataBlock> flatInputData = dataPreloadService.getData(taskId, 120); // 120s 超时
//
//            // C. [Structure Assembly] 结构组装 🔥 核心变化
//            // 将扁平的 Blocks 组装成容器需要的 List<List<TSDataBlock>> (按张量分组)
//            List<List<TSDataBlock>> structuredInputs = assembleInputGroups(task, flatInputData);
//
//            // D. [Compute Path] 执行计算
//            // 容器现在接受二维列表，返回二维列表
//            List<List<TSDataBlock>> structuredResults = containerPool.executeModel(runtimeId, structuredInputs);
//
//            // E. [Recursion] 结果处理与递归
//            if (structuredResults != null && !structuredResults.isEmpty()) {
//                handleResults(task, structuredResults);
//            }
//
//        } catch (Exception e) {
//            log.error("❌ [Orch] Task-{} (Model {}) 执行失败: {}", taskId, runtimeId, e.getMessage());
//            e.printStackTrace();
//        }
//    }
//
//    // ========================================================================
//    // 3. 结果处理 (Result Handling)
//    // ========================================================================
//
//    private void handleResults(ExecutableTask task, List<List<TSDataBlock>> structuredResults) {
//        List<ExecutableTask.TaskPort> outputPorts = task.getOutputs();
//
//        // 1. 校验输出数量是否匹配
//        if (structuredResults.size() != outputPorts.size()) {
//            log.warn("⚠️ [Orch] Task-{} 输出张量数不匹配: 预期 {}, 实际 {}",
//                    task.getTaskId(), outputPorts.size(), structuredResults.size());
//        }
//
//        // 2. 遍历每个输出端口 (Tensor Output)
//        for (int i = 0; i < structuredResults.size(); i++) {
//            if (i >= outputPorts.size()) break;
//
//            List<TSDataBlock> featureBlocks = structuredResults.get(i);
//            ExecutableTask.TaskPort port = outputPorts.get(i);
//
//            // 3. 遍历端口内的每个特征块 (Feature Block)
//            for (TSDataBlock resultBlock : featureBlocks) {
//                // 落库
//                dataService.pushData(resultBlock.getFeatureId(), resultBlock);
//
//                // 计算结束时间 (用于通知)
//                Instant tEnd = calculateEndTime(resultBlock);
//
//                // 递归通知下游
//                this.onDataChanged(
//                        resultBlock.getFeatureId(),
//                        resultBlock.getTOrigin(),
//                        tEnd
//                );
//            }
//        }
//
//        log.info("✅ [Orch] Task-{} 完成，处理了 {} 组输出张量。", task.getTaskId(), structuredResults.size());
//    }
//
//    // ========================================================================
//    // 4. 辅助方法 (Helpers)
//    // ========================================================================
//
//    /**
//     * 🔥 核心组装逻辑：Flat List -> Structured Group
//     * 将预取到的无序/扁平数据，按照 Task 定义的结构填入对应的位置。
//     */
//    private List<List<TSDataBlock>> assembleInputGroups(ExecutableTask task, List<TSDataBlock> flatData) {
//        // 1. 建立 FeatureID -> Block 的索引，方便快速查找
//        // 假设一次任务中 FeatureID 不重复 (如果重复需结合 Time/Shell 索引，这里简化处理)
//        Map<Integer, TSDataBlock> dataMap = flatData.stream()
//                .collect(Collectors.toMap(TSDataBlock::getFeatureId, Function.identity(), (existing, replacement) -> existing));
//
//        List<List<TSDataBlock>> inputGroups = new ArrayList<>();
//
//        // 2. 遍历任务定义的输入端口 (张量)
//        for (ExecutableTask.TaskPort port : task.getInputs()) {
//            List<TSDataBlock> portBlocks = new ArrayList<>();
//
//            // 3. 遍历端口需要的原子 Shell
//            for (TSShell shell : port.getAtomicShells()) {
//                TSDataBlock block = dataMap.get(shell.getFeatureId());
//
//                if (block == null) {
//                    throw new IllegalStateException("缺少必要输入数据: Feature " + shell.getFeatureId());
//                }
//                portBlocks.add(block);
//            }
//            inputGroups.add(portBlocks);
//        }
//        return inputGroups;
//    }
//
//    /**
//     * 根据 TimeAxis 计算数据块的结束时间
//     */
//    private Instant calculateEndTime(TSDataBlock block) {
//        TimeAxis tAxis = block.getTAxis();
//        if (tAxis == null) return block.getTOrigin();
//
//        // Count * Resolution = Total Duration
//        long duration = Math.round(tAxis.getResolution() * tAxis.getCount());
//
//        List<Instant> batchOrigins = block.getBatchTOrigins();
//        Instant lastOrigin = (batchOrigins != null && !batchOrigins.isEmpty())
//                ? batchOrigins.get(batchOrigins.size() - 1)
//                : block.getTOrigin();
//
//        if (lastOrigin == null) return Instant.now();
//        return lastOrigin.plusSeconds(duration);
//    }
//}