package com.example.lazarus_backend00.component.orchestration;

import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSState;
import com.example.lazarus_backend00.domain.data.TSShell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.BitSet;

public class ExecutableTask {

    private final long taskId;
    private final int containerId;

    // 核心指示：是否启用二值掩膜 (对应 Python 中的 applyMask)
    private final boolean applyMask;

    private final List<TaskPort> inputs;
    private final List<TaskPort> outputs;

    public ExecutableTask(long taskId, int containerId, boolean applyMask,
                          List<TaskPort> inputs, List<TaskPort> outputs) {
        this.taskId = taskId;
        this.containerId = containerId;
        this.applyMask = applyMask;
        this.inputs = inputs != null ? inputs : Collections.emptyList();
        this.outputs = outputs != null ? outputs : Collections.emptyList();
    }

    public long getTaskId() { return taskId; }
    public int getContainerId() { return containerId; }
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
     * 算法要求：
     * applyMask = true (状态0)：把已有的 1 和 2 对应的像素标为 NaN(舍弃)
     * applyMask = false (状态2)：直接输出全量数据，无视掩膜覆盖全部
     */
    public List<List<TSDataBlock>> filterRedundantOutputs(List<List<TSDataBlock>> rawOutputs) {
        // 不启用掩膜，直接覆盖
        if (!applyMask) {
            return rawOutputs;
        }

        List<List<TSDataBlock>> filteredOutputs = new ArrayList<>();

        for (int i = 0; i < rawOutputs.size(); i++) {
            List<TSDataBlock> rawGroup = rawOutputs.get(i);
            TaskPort portConf = this.outputs.get(i);
            List<TSDataBlock> filteredGroup = new ArrayList<>();

            for (int j = 0; j < rawGroup.size(); j++) {
                TSDataBlock rawBlock = rawGroup.get(j);
                List<TSState> featureTargetStates = portConf.getTargetStatesPerFeature().get(j);

                if (featureTargetStates != null && !featureTargetStates.isEmpty()) {
                    float[] data = rawBlock.getData();
                    int frameSize = data.length / featureTargetStates.size();
                    boolean hasValidData = false;

                    for (int t = 0; t < featureTargetStates.size(); t++) {
                        TSState targetState = featureTargetStates.get(t);

                        // 获取大盘上的空洞情况 (true 代表这是状态 0 的格子)
                        BitSet missingHoles = targetState.getMissingHolesMask();
                        int offset = t * frameSize;

                        for (int k = 0; k < frameSize; k++) {
                            if (!missingHoles.get(k)) {
                                // 如果这格子不是 0（即已经是 1 或 2），将模型输出设为 NaN 舍弃
                                data[offset + k] = Float.NaN;
                            } else {
                                // 验证确实算出了有效数据
                                if (!Float.isNaN(data[offset + k])) {
                                    hasValidData = true;
                                }
                            }
                        }
                    }

                    if (hasValidData) filteredGroup.add(rawBlock);
                } else {
                    filteredGroup.add(rawBlock);
                }
            }
            if (!filteredGroup.isEmpty()) filteredOutputs.add(filteredGroup);
        }
        return filteredOutputs;
    }

    public static class TaskPort {
        private final int order;
        private final List<TSShell> atomicShells;
        private final List<List<TSState>> targetStatesPerFeature;

        public TaskPort(int order, List<TSShell> atomicShells, List<List<TSState>> targetStatesPerFeature) {
            this.order = order;
            this.atomicShells = atomicShells;
            this.targetStatesPerFeature = targetStatesPerFeature != null ? targetStatesPerFeature : new ArrayList<>();
        }
        public int getOrder() { return order; }
        public List<TSShell> getAtomicShells() { return atomicShells; }
        public List<List<TSState>> getTargetStatesPerFeature() { return targetStatesPerFeature; }
    }
}