package com.example.lazarus_backend00.component.orchestration;

import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSState;
import com.example.lazarus_backend00.domain.data.TSShell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.BitSet;

/**
 * 可执行任务单 (像元级位图过滤版)
 * 职责：携带触发器算好的位图状态 (TSState)，并在获得输出后，执行真实的冗余数据擦除。
 */
public class ExecutableTask {

    private final long taskId;
    private final int containerId;
    private final boolean isReplacedCorrection;

    private final List<TaskPort> inputs;
    private final List<TaskPort> outputs;

    public ExecutableTask(long taskId, int containerId, boolean isReplacedCorrection,
                          List<TaskPort> inputs, List<TaskPort> outputs) {
        this.taskId = taskId;
        this.containerId = containerId;
        this.isReplacedCorrection = isReplacedCorrection;
        this.inputs = inputs != null ? inputs : Collections.emptyList();
        this.outputs = outputs != null ? outputs : Collections.emptyList();
    }

    public long getTaskId() { return taskId; }
    public int getContainerId() { return containerId; }
    public boolean isReplacedCorrection() { return isReplacedCorrection; }
    public List<TaskPort> getInputs() { return inputs; }
    public List<TaskPort> getOutputs() { return outputs; }

    public List<TSShell> getAllInputShells() {
        List<TSShell> allShells = new ArrayList<>();
        for (TaskPort port : inputs) {
            allShells.addAll(port.getAtomicShells());
        }
        return allShells;
    }

    /**
     * 【位图级真实裁剪引擎】
     * 遍历容器算出的 Float 数组，对照总线中的位图状态：
     * 如果该像元在底图中已经有数据了（不是空洞），就把它擦除（置为 NaN），防止覆写历史合法数据。
     */
    public List<List<TSDataBlock>> filterRedundantOutputs(List<List<TSDataBlock>> rawOutputs) {
        // 如果是纠偏任务，强制全量覆盖，不需要过滤
        if (isReplacedCorrection) {
            return rawOutputs;
        }

        List<List<TSDataBlock>> filteredOutputs = new ArrayList<>();

        for (int i = 0; i < rawOutputs.size(); i++) {
            List<TSDataBlock> rawGroup = rawOutputs.get(i);
            TaskPort portConf = this.outputs.get(i);
            List<TSDataBlock> filteredGroup = new ArrayList<>();

            for (int j = 0; j < rawGroup.size(); j++) {
                TSDataBlock rawBlock = rawGroup.get(j);
                TSState targetState = portConf.getTargetStates().get(j);

                if (targetState != null) {
                    // 1. 获取触发器算好的“空洞掩码”：true 代表没数据（需要填），false 代表有数据（需要抠掉）
                    BitSet missingHoles = targetState.getMissingHolesMask();
                    float[] data = rawBlock.getData();

                    // 2. 真实的一维数组遍历与位图核对
                    boolean hasValidData = false;
                    for (int k = 0; k < data.length; k++) {
                        if (!missingHoles.get(k)) {
                            // 已经被底图占据的位置，新算出来的数据作废，置为无数据
                            data[k] = Float.NaN;
                        } else {
                            hasValidData = true; // 只要有一个格子是有效填补的，这个 Block 就有价值
                        }
                    }

                    // 3. 只有当这个块里确实包含有效的新数据时，才放行
                    if (hasValidData) {
                        filteredGroup.add(rawBlock);
                    }
                } else {
                    // 如果没有限制状态，全量放行
                    filteredGroup.add(rawBlock);
                }
            }
            if (!filteredGroup.isEmpty()) {
                filteredOutputs.add(filteredGroup);
            }
        }
        return filteredOutputs;
    }

    // ================== 内部类：任务端口 ==================
    public static class TaskPort {
        private final int order;
        private final List<TSShell> atomicShells;

        // 核心变更：这里现在是 TSState，而不是 Envelope！
        private final List<TSState> targetStates;

        public TaskPort(int order, List<TSShell> atomicShells, List<TSState> targetStates) {
            this.order = order;
            this.atomicShells = atomicShells;
            this.targetStates = targetStates != null ? targetStates : new ArrayList<>();
        }

        public int getOrder() { return order; }
        public List<TSShell> getAtomicShells() { return atomicShells; }
        public List<TSState> getTargetStates() { return targetStates; }
    }
}