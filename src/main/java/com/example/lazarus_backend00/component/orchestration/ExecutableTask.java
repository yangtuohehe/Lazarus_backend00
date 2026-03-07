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
     * 【真正的三维时空像素级裁剪引擎】
     * 严谨对齐时间轴，按帧 (Frame) 提取对应的 TSState 位图，进行地理交叠擦除。
     */
    public List<List<TSDataBlock>> filterRedundantOutputs(List<List<TSDataBlock>> rawOutputs) {
        if (isReplacedCorrection) return rawOutputs;

        List<List<TSDataBlock>> filteredOutputs = new ArrayList<>();

        for (int i = 0; i < rawOutputs.size(); i++) {
            List<TSDataBlock> rawGroup = rawOutputs.get(i);
            TaskPort portConf = this.outputs.get(i);
            List<TSDataBlock> filteredGroup = new ArrayList<>();

            for (int j = 0; j < rawGroup.size(); j++) {
                TSDataBlock rawBlock = rawGroup.get(j);

                // 🎯 拿到该特征对应的 12 个时间帧的独立地理掩码
                List<TSState> featureTargetStates = portConf.getTargetStatesPerFeature().get(j);

                if (featureTargetStates != null && !featureTargetStates.isEmpty()) {
                    float[] data = rawBlock.getData();

                    // 计算单帧的二维网格大小 (例如 100x100 = 10000)
                    int frameSize = data.length / featureTargetStates.size();
                    boolean hasValidData = false;

                    // 🎯 逐帧进行时空遍历
                    for (int t = 0; t < featureTargetStates.size(); t++) {
                        TSState targetState = featureTargetStates.get(t);
                        BitSet missingHoles = targetState.getMissingHolesMask();
                        int offset = t * frameSize; // 该帧在三维数组中的偏移量

                        for (int k = 0; k < frameSize; k++) {
                            // !missingHoles 代表这里大盘已经有观测数据了
                            if (!missingHoles.get(k)) {
                                data[offset + k] = Float.NaN; // 严谨擦除
                            } else {
                                // 如果没被擦除，且模型确实算出了有效值
                                if (!Float.isNaN(data[offset + k])) {
                                    hasValidData = true;
                                }
                            }
                        }
                    }

                    // 只要这 12 帧里有哪怕 1 个像素是有效的增量数据，就保留这个 Block
                    if (hasValidData) {
                        filteredGroup.add(rawBlock);
                    }
                } else {
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

        // 🎯 核心变更：从 List<TSState> 变为 List<List<TSState>> (Feature -> TimeSteps)
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