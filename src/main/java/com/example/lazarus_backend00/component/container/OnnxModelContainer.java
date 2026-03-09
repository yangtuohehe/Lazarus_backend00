//package com.example.lazarus_backend00.component.container;
//
//import ai.onnxruntime.*;
//import com.example.lazarus_backend00.dao.DynamicProcessModelDao;
//import com.example.lazarus_backend00.domain.axis.Axis;
//import com.example.lazarus_backend00.domain.axis.Feature;
//import com.example.lazarus_backend00.domain.axis.TimeAxis;
//import com.example.lazarus_backend00.domain.data.TSDataBlock;
//import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;
//
//import java.nio.FloatBuffer;
//import java.time.Instant;
//import java.util.*;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.concurrent.atomic.AtomicReference;
//import java.util.stream.Collectors;
//
//public class OnnxModelContainer implements ModelContainer {
//
//    private final int containerId;
//    private final Integer version;
//    private final List<Parameter> parameterList;
//    private final DynamicProcessModelDao modelDao;
//    private final int estimatedModelSizeBytes;
//    private final AtomicReference<ContainerStatus> status;
//    private final AtomicInteger runtimeMemoryUsage;
//
//    private OrtEnvironment env;
//    private OrtSession session;
//
//    public OnnxModelContainer(int containerId, Integer version, List<Parameter> parameterList,
//                              DynamicProcessModelDao modelDao, int modelSizeBytes) {
//        this.containerId = containerId;
//        this.version = version;
//        this.parameterList = parameterList;
//        this.modelDao = modelDao;
//        this.estimatedModelSizeBytes = modelSizeBytes;
//        this.status = new AtomicReference<>(ContainerStatus.CREATED);
//        this.runtimeMemoryUsage = new AtomicInteger(0);
//    }
//
//    @Override
//    public boolean load() {
//        if (status.get() == ContainerStatus.LOADED) return true;
//        byte[] tempModelArtifact = null;
//        try {
//            DynamicProcessModelEntity fileEntity = modelDao.selectModelFile(this.containerId);
//            if (fileEntity != null) tempModelArtifact = fileEntity.getModelFile();
//            if (tempModelArtifact == null) throw new RuntimeException("No model artifact found.");
//
//            this.env = OrtEnvironment.getEnvironment();
//            this.session = env.createSession(tempModelArtifact, new OrtSession.SessionOptions());
//            this.runtimeMemoryUsage.set(calculateMemoryUsage(tempModelArtifact.length));
//            this.status.set(ContainerStatus.LOADED);
//            return true;
//        } catch (Exception e) {
//            this.status.set(ContainerStatus.FAILED);
//            e.printStackTrace();
//            return false;
//        } finally {
//            tempModelArtifact = null;
//        }
//    }
//
//    @Override
//    public boolean unload() {
//        try {
//            if (session != null) { session.close(); session = null; }
//            if (env != null) { env.close(); env = null; }
//            this.runtimeMemoryUsage.set(0);
//            this.status.set(ContainerStatus.UNLOADED);
//            return true;
//        } catch (Exception e) { return false; }
//    }
//
//    @Override
//    public List<List<TSDataBlock>> run(List<List<TSDataBlock>> inputGroups) {
//        if (status.get() != ContainerStatus.LOADED) throw new IllegalStateException("Not loaded.");
//        this.runtimeMemoryUsage.addAndGet(50);
//
//        Map<String, OnnxTensor> inputTensorMap = new HashMap<>();
//        try {
//            Iterator<String> inputNameIter = session.getInputNames().iterator();
//            for (List<TSDataBlock> group : inputGroups) {
//                String inputNodeName = inputNameIter.next();
//                TSDataBlock template = group.get(0);
//                int singleBlockSize = template.getData().length;
//                FloatBuffer buffer = FloatBuffer.allocate(singleBlockSize * group.size());
//                for (TSDataBlock block : group) buffer.put(block.getData());
//                buffer.flip();
//
//                long[] newShape = adjustShapeForMultiChannel(template.getDynamicShape(), group.size());
//                inputTensorMap.put(inputNodeName, OnnxTensor.createTensor(env, buffer, newShape));
//            }
//
//            try (OrtSession.Result results = session.run(inputTensorMap)) {
//                return parseOutputs(results, inputGroups.get(0).get(0));
//            }
//        } catch (Exception e) {
//            this.status.set(ContainerStatus.FAILED);
//            throw new RuntimeException("ONNX run failed", e);
//        } finally {
//            for (OnnxTensor t : inputTensorMap.values()) if (t != null) t.close();
//            this.runtimeMemoryUsage.addAndGet(-50);
//        }
//    }
//
//    private long[] adjustShapeForMultiChannel(long[] originalShape, int channelCount) {
//        long[] newShape = new long[originalShape.length + 1];
//        newShape[0] = originalShape[0];
//        newShape[1] = originalShape[1];
//        newShape[2] = channelCount;
//        System.arraycopy(originalShape, 2, newShape, 3, originalShape.length - 2);
//        return newShape;
//    }
//
//    private List<List<TSDataBlock>> parseOutputs(OrtSession.Result results, TSDataBlock templateBlock) {
//        List<List<TSDataBlock>> allOutputs = new ArrayList<>();
//        List<Parameter> outputParams = getSortedOutputParameters();
//
//
//        Iterator<Map.Entry<String, OnnxValue>> resultIter = results.iterator();
//        for (Parameter param : outputParams) {
//            if (!resultIter.hasNext()) break;
//            OnnxValue value = resultIter.next().getValue();
//            if (!(value instanceof OnnxTensor)) continue;
//
//            try {
//                float[] tensorData = ((OnnxTensor) value).getFloatBuffer().array();
//                int channelCount = param.getFeatureList().size();
//
//                // 🎯 核心诊断：单通道的长度
//                int singleBlockLength = tensorData.length / channelCount;
//
//                System.out.println("🔧 [ONNX Container] Tensor Size: " + tensorData.length +
//                        ", Channel Count: " + channelCount +
//                        ", Elements per Block: " + singleBlockLength);
//
//                List<TSDataBlock> portOutputs = new ArrayList<>();
//
//                for (int c = 0; c < channelCount; c++) {
//                    Feature f = param.getFeatureList().get(c);
//                    float[] slice = new float[singleBlockLength];
//
//                    // ⚠️ 极其关键：确保跨通道切片不会发生交叠
//                    System.arraycopy(tensorData, c * singleBlockLength, slice, 0, singleBlockLength);
//
//                    TSDataBlock.Builder builder = new TSDataBlock.Builder()
//                            .featureId(f.getId())
//                            .data(slice);
//
//                    applyOutputMetadata(param, templateBlock, builder); // <--- 🔥 传入整个 templateBlock
//                    portOutputs.add(builder.build());
//                    portOutputs.add(builder.build());
//                }
//                allOutputs.add(portOutputs);
//            } catch (Exception e) {
//                throw new RuntimeException("Parse ONNX output failed", e);
//            }
//        }
//        return allOutputs;
//    }
//
//    private void applyOutputMetadata(Parameter param, TSDataBlock templateBlock, TSDataBlock.Builder builder) {
//
//        Instant baseTOrigin = templateBlock.getTOrigin();
//
//        // 1. 注入输出时间轴
//        if (param.getTimeAxis() != null && baseTOrigin != null) {
//            Instant actualOutputTime = baseTOrigin;
//
//            if (param.getoTimeStep() != null) {
//                // 🌟🌟🌟 新核心逻辑：输出时间 = 模型逻辑零点(输入的起点) + (oTimeStep偏移量 * 输出时间轴的步长)
//                long offsetSeconds = param.getoTimeStep() * getAxisResolutionInSeconds(param.getTimeAxis());
//                actualOutputTime = baseTOrigin.plusSeconds(offsetSeconds);
//            } else {
//                // 🔙 兼容回退逻辑：如果数据库没配 oTimeStep，默认假定它接在输入数据的屁股后面
//                TimeAxis inputTAxis = templateBlock.getTAxis();
//                if (inputTAxis != null && inputTAxis.getCount() != null) {
//                    long totalInputDurationSec = inputTAxis.getCount() * getAxisResolutionInSeconds(inputTAxis);
//                    actualOutputTime = baseTOrigin.plusSeconds(totalInputDurationSec);
//                }
//            }
//
//            builder.time(actualOutputTime, param.getTimeAxis());
//        }
//
//        // 2. 注入输出空间轴 (原有代码保持不变)
//        if (param.getAxisList() != null) {
//            for (com.example.lazarus_backend00.domain.axis.Axis axis : param.getAxisList()) {
//                if (axis instanceof com.example.lazarus_backend00.domain.axis.SpaceAxisX) {
//                    builder.x(param.getOriginPoint().getX(), (com.example.lazarus_backend00.domain.axis.SpaceAxisX) axis);
//                } else if (axis instanceof com.example.lazarus_backend00.domain.axis.SpaceAxisY) {
//                    builder.y(param.getOriginPoint().getY(), (com.example.lazarus_backend00.domain.axis.SpaceAxisY) axis);
//                } else if (axis instanceof com.example.lazarus_backend00.domain.axis.SpaceAxisZ) {
//                    builder.z(param.getOriginPoint().getCoordinate().getZ(), (com.example.lazarus_backend00.domain.axis.SpaceAxisZ) axis);
//                }
//            }
//        }
//    }
//
//    private long getAxisResolutionInSeconds(TimeAxis tAxis) {
//        if (tAxis == null || tAxis.getResolution() == null) return 0;
//        double res = tAxis.getResolution();
//        String unit = (tAxis.getUnit() != null) ? tAxis.getUnit().trim().toLowerCase() : "s";
//        if (unit.startsWith("h")) return (long) (res * 3600);
//        if (unit.startsWith("m")) return (long) (res * 60);
//        return (long) res;
//    }
//
//    private List<Parameter> getSortedOutputParameters() {
//        return parameterList.stream()
//                .filter(p -> "OUTPUT".equalsIgnoreCase(p.getIoType()))
//                .sorted(Comparator.comparingInt(Parameter::getTensorOrder))
//                .collect(Collectors.toList());
//    }
//
//    private int calculateMemoryUsage(long weightSizeBytes) {
//        long staticMem = (long) (weightSizeBytes * 1.2);
//        long ioMemBytes = 100 * 1024 * 1024L; // 简化版估算
//        return (int) Math.ceil((staticMem + ioMemBytes * 3.0 + 50 * 1024 * 1024L) / (1024.0 * 1024.0));
//    }
//
//
//
//    @Override public int getContainerId() { return containerId; }
//    @Override public Integer getVersion() { return version; }
//    @Override public List<Parameter> getParameterList() { return parameterList; }
//    @Override public ContainerStatus getStatus() { return status.get(); }
//    @Override public int getMemoryUsage() { return runtimeMemoryUsage.get(); }
//}
package com.example.lazarus_backend00.component.container;

import ai.onnxruntime.*;
import com.example.lazarus_backend00.dao.DynamicProcessModelDao;
import com.example.lazarus_backend00.domain.axis.Axis;
import com.example.lazarus_backend00.domain.axis.Feature;
import com.example.lazarus_backend00.domain.axis.SpaceAxisX;
import com.example.lazarus_backend00.domain.axis.SpaceAxisY;
import com.example.lazarus_backend00.domain.axis.SpaceAxisZ;
import com.example.lazarus_backend00.domain.axis.TimeAxis;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;

import java.nio.FloatBuffer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class OnnxModelContainer implements ModelContainer {

    private final int containerId;
    private final Integer version;
    private final List<Parameter> parameterList;
    private final DynamicProcessModelDao modelDao;
    private final int estimatedModelSizeBytes;
    private final AtomicReference<ContainerStatus> status;
    private final AtomicInteger runtimeMemoryUsage;

    private OrtEnvironment env;
    private OrtSession session;

    public OnnxModelContainer(int containerId, Integer version, List<Parameter> parameterList,
                              DynamicProcessModelDao modelDao, int modelSizeBytes) {
        this.containerId = containerId;
        this.version = version;
        this.parameterList = parameterList;
        this.modelDao = modelDao;
        this.estimatedModelSizeBytes = modelSizeBytes;
        this.status = new AtomicReference<>(ContainerStatus.CREATED);
        this.runtimeMemoryUsage = new AtomicInteger(0);
    }

    @Override
    public boolean load() {
        if (status.get() == ContainerStatus.LOADED) return true;
        byte[] tempModelArtifact = null;
        try {
            DynamicProcessModelEntity fileEntity = modelDao.selectModelFile(this.containerId);
            if (fileEntity != null) tempModelArtifact = fileEntity.getModelFile();
            if (tempModelArtifact == null) throw new RuntimeException("No model artifact found.");

            this.env = OrtEnvironment.getEnvironment();
            this.session = env.createSession(tempModelArtifact, new OrtSession.SessionOptions());
            this.runtimeMemoryUsage.set(calculateMemoryUsage(tempModelArtifact.length));
            this.status.set(ContainerStatus.LOADED);
            return true;
        } catch (Exception e) {
            this.status.set(ContainerStatus.FAILED);
            e.printStackTrace();
            return false;
        } finally {
            tempModelArtifact = null;
        }
    }

    @Override
    public boolean unload() {
        try {
            if (session != null) { session.close(); session = null; }
            if (env != null) { env.close(); env = null; }
            this.runtimeMemoryUsage.set(0);
            this.status.set(ContainerStatus.UNLOADED);
            return true;
        } catch (Exception e) { return false; }
    }

    @Override
    public List<List<TSDataBlock>> run(List<List<TSDataBlock>> inputGroups) {
        if (status.get() != ContainerStatus.LOADED) throw new IllegalStateException("Not loaded.");
        this.runtimeMemoryUsage.addAndGet(50);

        Map<String, OnnxTensor> inputTensorMap = new HashMap<>();
        try {
            Iterator<String> inputNameIter = session.getInputNames().iterator();
            List<Parameter> inputParams = getSortedInputParameters();
            Iterator<Parameter> paramIter = inputParams.iterator();

            for (List<TSDataBlock> group : inputGroups) {
                String inputNodeName = inputNameIter.next();
                Parameter param = paramIter.next();

                TSDataBlock template = group.get(0);
                long[] dynamicShape = calculateShapeFromParameter(param, template.getBatchSize(), group.size());
                System.out.println("🚀 [ONNX Input] Node: " + inputNodeName + ", Dynamic Shape: " + Arrays.toString(dynamicShape));

                // 🌟 核心：根据 Parameter 动态组装输入张量的内存布局
                int channelIdx = getChannelDimensionIndex(param);
                FloatBuffer buffer = interleaveInputData(group, dynamicShape, channelIdx);

                inputTensorMap.put(inputNodeName, OnnxTensor.createTensor(env, buffer, dynamicShape));
            }

            try (OrtSession.Result results = session.run(inputTensorMap)) {
                return parseOutputs(results, inputGroups.get(0).get(0));
            }
        } catch (Exception e) {
            this.status.set(ContainerStatus.FAILED);
            throw new RuntimeException("ONNX run failed", e);
        } finally {
            for (OnnxTensor t : inputTensorMap.values()) if (t != null) t.close();
            this.runtimeMemoryUsage.addAndGet(-50);
        }
    }

    private List<List<TSDataBlock>> parseOutputs(OrtSession.Result results, TSDataBlock templateBlock) {
        List<List<TSDataBlock>> allOutputs = new ArrayList<>();
        List<Parameter> outputParams = getSortedOutputParameters();

        Iterator<Map.Entry<String, OnnxValue>> resultIter = results.iterator();
        int validOutputCount = Math.min((int)results.size(), outputParams.size());

        for (int i = 0; i < validOutputCount; i++) {
            Parameter param = outputParams.get(i);
            if (!resultIter.hasNext()) break;
            OnnxValue value = resultIter.next().getValue();
            if (!(value instanceof OnnxTensor)) continue;

            try {
                OnnxTensor tensor = (OnnxTensor) value;
                float[] tensorData = tensor.getFloatBuffer().array();

                long[] actualShape = tensor.getInfo().getShape();
                int channelIdx = getChannelDimensionIndex(param);

                int actualChannels = (actualShape.length > channelIdx) ? (int)actualShape[channelIdx] : 1;
                int configuredChannels = param.getFeatureList().size();

                System.out.println("🔧 [ONNX Output] 真实 Shape: " + Arrays.toString(actualShape) +
                        ", 特征维度索引: " + channelIdx +
                        ", 真实特征数: " + actualChannels);

                int safeChannelCount = Math.min(actualChannels, configuredChannels);

                // 🌟 核心：根据 Parameter 动态拆解输出张量的内存布局
                List<float[]> separatedChannels = deinterleaveOutputData(tensorData, actualShape, channelIdx, safeChannelCount);

                List<TSDataBlock> portOutputs = new ArrayList<>();
                for (int c = 0; c < safeChannelCount; c++) {
                    Feature f = param.getFeatureList().get(c);
                    float[] channelData = separatedChannels.get(c);

                    TSDataBlock.Builder builder = new TSDataBlock.Builder()
                            .featureId(f.getId())
                            .data(channelData);

                    applyOutputMetadata(param, templateBlock, builder);
                    portOutputs.add(builder.build());
                }
                allOutputs.add(portOutputs);
            } catch (Exception e) {
                throw new RuntimeException("Parse ONNX output failed", e);
            }
        }
        return allOutputs;
    }

    // =========================================================================
    // 🌟 全新组件：根据 Parameter 动态内存布局转换 (Interleave / Deinterleave)
    // =========================================================================

    /**
     * 将多个 Channel 的 TSDataBlock，根据 Channel 在 Shape 中的实际维度索引，
     * 交错拼装成 ONNX 底层 C 语言期望的连续内存。
     */
    private FloatBuffer interleaveInputData(List<TSDataBlock> group, long[] shape, int channelIdx) {
        int totalElements = group.get(0).getData().length * group.size();
        FloatBuffer buffer = FloatBuffer.allocate(totalElements);

        // 如果 Channel 是最外层的维度 (即在 Batch 后面的索引 1)，无需交错，直接顺序拼接最高效
        if (channelIdx == 1) {
            for (TSDataBlock block : group) buffer.put(block.getData());
            buffer.flip();
            return buffer;
        }

        // 如果 Channel 被插在了深层 (例如 [Batch, Time, Channel, Y, X])，必须执行交错拼装
        int channelCount = group.size();
        int elementsPerChannel = group.get(0).getData().length;

        // 计算 Channel 维度之外，其外部的块数 (外块) 和内部的块大小 (内块)
        // 例如 [1, 24, 4, 24, 24], Channel在索引2。
        // 外块(Batch * Time) = 1 * 24 = 24
        // 内块(Y * X) = 24 * 24 = 576
        int outerBlocks = 1;
        for (int i = 0; i < channelIdx; i++) outerBlocks *= (int) shape[i];

        int innerBlockSize = 1;
        for (int i = channelIdx + 1; i < shape.length; i++) innerBlockSize *= (int) shape[i];

        // 执行交错拷贝
        for (int ob = 0; ob < outerBlocks; ob++) {
            for (int c = 0; c < channelCount; c++) {
                float[] sourceData = group.get(c).getData();
                int srcOffset = ob * innerBlockSize;
                buffer.put(sourceData, srcOffset, innerBlockSize);
            }
        }

        buffer.flip();
        return buffer;
    }

    /**
     * 将 ONNX 返回的连续内存，根据 Channel 的实际维度索引，
     * 反向拆解并还原为各个独立的 Channel 一维数组。
     */
    private List<float[]> deinterleaveOutputData(float[] tensorData, long[] shape, int channelIdx, int safeChannelCount) {
        List<float[]> separatedChannels = new ArrayList<>(safeChannelCount);

        int totalElementsPerChannel = tensorData.length / (int)shape[channelIdx];

        // 如果 Channel 在最外层 (索引 1)，无需交错解包，直接大块切割
        if (channelIdx == 1) {
            for (int c = 0; c < safeChannelCount; c++) {
                float[] slice = new float[totalElementsPerChannel];
                System.arraycopy(tensorData, c * totalElementsPerChannel, slice, 0, totalElementsPerChannel);
                separatedChannels.add(slice);
            }
            return separatedChannels;
        }

        // 如果 Channel 在深层，执行交错反解包
        int outerBlocks = 1;
        for (int i = 0; i < channelIdx; i++) outerBlocks *= (int) shape[i];

        int innerBlockSize = 1;
        for (int i = channelIdx + 1; i < shape.length; i++) innerBlockSize *= (int) shape[i];

        // 我们需要知道真实输出的通道数，以计算步长
        int actualChannels = (int) shape[channelIdx];

        for (int c = 0; c < safeChannelCount; c++) {
            float[] slice = new float[totalElementsPerChannel];
            for (int ob = 0; ob < outerBlocks; ob++) {
                int srcOffset = (ob * actualChannels + c) * innerBlockSize;
                int destOffset = ob * innerBlockSize;
                System.arraycopy(tensorData, srcOffset, slice, destOffset, innerBlockSize);
            }
            separatedChannels.add(slice);
        }

        return separatedChannels;
    }

    // =========================================================================
    // 🌟 纯粹版逻辑：完全遵守 "Batch=0, 轴各就各位, 剩余归特征"
    // =========================================================================
    private long[] calculateShapeFromParameter(Parameter param, int batchSize, int channelCount) {
        List<Axis> axes = param.getAxisList();
        int totalDims = 1 + 1 + (axes != null ? axes.size() : 0);
        long[] shape = new long[totalDims];
        boolean[] occupied = new boolean[totalDims];

        shape[0] = batchSize;
        occupied[0] = true;

        if (axes != null) {
            for (Axis axis : axes) {
                int dimIdx = axis.getDimensionIndex();
                shape[dimIdx] = axis.getCount();
                occupied[dimIdx] = true;
            }
        }

        for (int i = 1; i < totalDims; i++) {
            if (!occupied[i]) {
                shape[i] = channelCount;
                occupied[i] = true;
                break;
            }
        }
        return shape;
    }

    private int getChannelDimensionIndex(Parameter param) {
        List<Axis> axes = param.getAxisList();
        int totalDims = 1 + 1 + (axes != null ? axes.size() : 0);
        boolean[] occupied = new boolean[totalDims];
        occupied[0] = true;

        if (axes != null) {
            for (Axis axis : axes) {
                occupied[axis.getDimensionIndex()] = true;
            }
        }

        for (int i = 1; i < totalDims; i++) {
            if (!occupied[i]) return i;
        }
        return 1;
    }
    // =========================================================================

    private void applyOutputMetadata(Parameter param, TSDataBlock templateBlock, TSDataBlock.Builder builder) {
        Instant baseTOrigin = templateBlock.getTOrigin();

        if (param.getTimeAxis() != null && baseTOrigin != null) {
            Instant actualOutputTime = baseTOrigin;
            if (param.getoTimeStep() != null) {
                long offsetSeconds = param.getoTimeStep() * getAxisResolutionInSeconds(param.getTimeAxis());
                actualOutputTime = baseTOrigin.plusSeconds(offsetSeconds);
            } else {
                TimeAxis inputTAxis = templateBlock.getTAxis();
                if (inputTAxis != null && inputTAxis.getCount() != null) {
                    long totalInputDurationSec = inputTAxis.getCount() * getAxisResolutionInSeconds(inputTAxis);
                    actualOutputTime = baseTOrigin.plusSeconds(totalInputDurationSec);
                }
            }
            int originalIdx = param.getTimeAxis().getDimensionIndex();
            builder.time(actualOutputTime, param.getTimeAxis());
            param.getTimeAxis().setDimensionIndex(originalIdx);
        }

        if (param.getAxisList() != null) {
            for (com.example.lazarus_backend00.domain.axis.Axis axis : param.getAxisList()) {
                int originalIdx = axis.getDimensionIndex();
                if (axis instanceof com.example.lazarus_backend00.domain.axis.SpaceAxisX) {
                    builder.x(param.getOriginPoint().getX(), (com.example.lazarus_backend00.domain.axis.SpaceAxisX) axis);
                } else if (axis instanceof com.example.lazarus_backend00.domain.axis.SpaceAxisY) {
                    builder.y(param.getOriginPoint().getY(), (com.example.lazarus_backend00.domain.axis.SpaceAxisY) axis);
                } else if (axis instanceof com.example.lazarus_backend00.domain.axis.SpaceAxisZ) {
                    builder.z(param.getOriginPoint().getCoordinate().getZ(), (com.example.lazarus_backend00.domain.axis.SpaceAxisZ) axis);
                }
                axis.setDimensionIndex(originalIdx);
            }
        }
    }

    private long getAxisResolutionInSeconds(TimeAxis tAxis) {
        if (tAxis == null || tAxis.getResolution() == null) return 0;
        double res = tAxis.getResolution();
        String unit = (tAxis.getUnit() != null) ? tAxis.getUnit().trim().toLowerCase() : "s";
        if (unit.startsWith("h")) return (long) (res * 3600);
        if (unit.startsWith("m")) return (long) (res * 60);
        return (long) res;
    }

    private List<Parameter> getSortedInputParameters() {
        return parameterList.stream()
                .filter(p -> "INPUT".equalsIgnoreCase(p.getIoType()))
                .sorted(Comparator.comparingInt(Parameter::getTensorOrder))
                .collect(Collectors.toList());
    }

    private List<Parameter> getSortedOutputParameters() {
        return parameterList.stream()
                .filter(p -> "OUTPUT".equalsIgnoreCase(p.getIoType()))
                .sorted(Comparator.comparingInt(Parameter::getTensorOrder))
                .collect(Collectors.toList());
    }

    private int calculateMemoryUsage(long weightSizeBytes) {
        long staticMem = (long) (weightSizeBytes * 1.2);
        long ioMemBytes = 100 * 1024 * 1024L;
        return (int) Math.ceil((staticMem + ioMemBytes * 3.0 + 50 * 1024 * 1024L) / (1024.0 * 1024.0));
    }

    @Override public int getContainerId() { return containerId; }
    @Override public Integer getVersion() { return version; }
    @Override public List<Parameter> getParameterList() { return parameterList; }
    @Override public ContainerStatus getStatus() { return status.get(); }
    @Override public int getMemoryUsage() { return runtimeMemoryUsage.get(); }
}