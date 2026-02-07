package com.example.lazarus_backend00.component.orchestration;

import com.example.lazarus_backend00.domain.data.TSShell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 可执行任务单 (层级化重构版)
 * 职责：
 * 1. 身份凭证：taskId 全局唯一。
 * 2. 结构化描述：将“原子化的数据查询请求”按“模型的张量顺序”进行分组。
 */
public class ExecutableTask {

    // ================== 身份标识 ==================
    private final long taskId;
    private final int containerId;

    // ================== 任务结构 ==================
    // 列表索引 i 对应模型的第 i 个输入张量 (Parameter order)
    private final List<TaskPort> inputs;

    // 列表索引 i 对应模型的第 i 个输出张量
    private final List<TaskPort> outputs;

    public ExecutableTask(long taskId, int containerId, List<TaskPort> inputs, List<TaskPort> outputs) {
        this.taskId = taskId;
        this.containerId = containerId;
        this.inputs = inputs != null ? inputs : Collections.emptyList();
        this.outputs = outputs != null ? outputs : Collections.emptyList();
    }

    // ================== Getters ==================
    public long getTaskId() { return taskId; }
    public int getContainerId() { return containerId; }
    public List<TaskPort> getInputs() { return inputs; }
    public List<TaskPort> getOutputs() { return outputs; }

    /**
     * 辅助方法：扁平化获取所有需要查询的 Shell (用于 DataPreloader 批量取数)
     * DataPreloader 不需要关心层级，它只需要拿到所有 Shell 去数据库捞数据。
     */
    public List<TSShell> getAllInputShells() {
        List<TSShell> allShells = new ArrayList<>();
        for (TaskPort port : inputs) {
            allShells.addAll(port.getAtomicShells());
        }
        return allShells;
    }

    // ================== 内部类：任务端口/张量组 ==================
    /**
     * TaskPort (对应模型的一个 Parameter / 一个张量)
     * 一个端口可能由多个原子特征组成 (例如：Input[0] 是一个包含 [温度, 湿度] 的 2通道张量)。
     */
    public static class TaskPort {
        // 对应 Parameter 中的 tensorOrder
        private final int order;

        // 该张量所需的原子数据外壳列表
        // 顺序必须严格对应 Parameter.featureList 的顺序
        private final List<TSShell> atomicShells;

        public TaskPort(int order, List<TSShell> atomicShells) {
            this.order = order;
            this.atomicShells = atomicShells;
        }

        public int getOrder() { return order; }
        public List<TSShell> getAtomicShells() { return atomicShells; }
    }
}