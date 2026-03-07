package com.example.lazarus_backend00.component.container;

import ai.onnxruntime.*;
import com.example.lazarus_backend00.dao.DynamicProcessModelDao;
import com.example.lazarus_backend00.domain.axis.Axis;
import com.example.lazarus_backend00.domain.axis.Feature;
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
            for (List<TSDataBlock> group : inputGroups) {
                String inputNodeName = inputNameIter.next();
                TSDataBlock template = group.get(0);
                int singleBlockSize = template.getData().length;
                FloatBuffer buffer = FloatBuffer.allocate(singleBlockSize * group.size());
                for (TSDataBlock block : group) buffer.put(block.getData());
                buffer.flip();

                long[] newShape = adjustShapeForMultiChannel(template.getDynamicShape(), group.size());
                inputTensorMap.put(inputNodeName, OnnxTensor.createTensor(env, buffer, newShape));
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

    private long[] adjustShapeForMultiChannel(long[] originalShape, int channelCount) {
        long[] newShape = new long[originalShape.length + 1];
        newShape[0] = originalShape[0];
        newShape[1] = originalShape[1];
        newShape[2] = channelCount;
        System.arraycopy(originalShape, 2, newShape, 3, originalShape.length - 2);
        return newShape;
    }

    private List<List<TSDataBlock>> parseOutputs(OrtSession.Result results, TSDataBlock templateBlock) {
        List<List<TSDataBlock>> allOutputs = new ArrayList<>();
        List<Parameter> outputParams = getSortedOutputParameters();

        // 动态计算预测输出的 TOrigin
        Instant outputTOrigin = templateBlock.getTOrigin();
        TimeAxis inputTAxis = templateBlock.getTAxis();
        if (outputTOrigin != null && inputTAxis != null && inputTAxis.getCount() != null) {
            long totalInputDurationSec = inputTAxis.getCount() * getAxisResolutionInSeconds(inputTAxis);
            outputTOrigin = outputTOrigin.plusSeconds(totalInputDurationSec);
        }

        Iterator<Map.Entry<String, OnnxValue>> resultIter = results.iterator();
        for (Parameter param : outputParams) {
            if (!resultIter.hasNext()) break;
            OnnxValue value = resultIter.next().getValue();
            if (!(value instanceof OnnxTensor)) continue;

            try {
                float[] tensorData = ((OnnxTensor) value).getFloatBuffer().array();
                int channelCount = param.getFeatureList().size();

                // 🎯 核心诊断：单通道的长度
                int singleBlockLength = tensorData.length / channelCount;

                System.out.println("🔧 [ONNX Container] Tensor Size: " + tensorData.length +
                        ", Channel Count: " + channelCount +
                        ", Elements per Block: " + singleBlockLength);

                List<TSDataBlock> portOutputs = new ArrayList<>();

                for (int c = 0; c < channelCount; c++) {
                    Feature f = param.getFeatureList().get(c);
                    float[] slice = new float[singleBlockLength];

                    // ⚠️ 极其关键：确保跨通道切片不会发生交叠
                    System.arraycopy(tensorData, c * singleBlockLength, slice, 0, singleBlockLength);

                    TSDataBlock.Builder builder = new TSDataBlock.Builder()
                            .featureId(f.getId())
                            .data(slice);

                    applyOutputMetadata(param, outputTOrigin, builder);
                    portOutputs.add(builder.build());
                }
                allOutputs.add(portOutputs);
            } catch (Exception e) {
                throw new RuntimeException("Parse ONNX output failed", e);
            }
        }
        return allOutputs;
    }

    private void applyOutputMetadata(Parameter param, Instant outputTOrigin, TSDataBlock.Builder builder) {
        if (param.getTimeAxis() != null && outputTOrigin != null) {
            builder.time(outputTOrigin, param.getTimeAxis());
        }
        if (param.getAxisList() != null) {
            for (Axis axis : param.getAxisList()) {
                if (axis instanceof com.example.lazarus_backend00.domain.axis.SpaceAxisX) {
                    builder.x(param.getOriginPoint().getX(), (com.example.lazarus_backend00.domain.axis.SpaceAxisX) axis);
                } else if (axis instanceof com.example.lazarus_backend00.domain.axis.SpaceAxisY) {
                    builder.y(param.getOriginPoint().getY(), (com.example.lazarus_backend00.domain.axis.SpaceAxisY) axis);
                } else if (axis instanceof com.example.lazarus_backend00.domain.axis.SpaceAxisZ) {
                    builder.z(param.getOriginPoint().getCoordinate().getZ(), (com.example.lazarus_backend00.domain.axis.SpaceAxisZ) axis);
                }
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

    private List<Parameter> getSortedOutputParameters() {
        return parameterList.stream()
                .filter(p -> "OUTPUT".equalsIgnoreCase(p.getIoType()))
                .sorted(Comparator.comparingInt(Parameter::getTensorOrder))
                .collect(Collectors.toList());
    }

    private int calculateMemoryUsage(long weightSizeBytes) {
        long staticMem = (long) (weightSizeBytes * 1.2);
        long ioMemBytes = 100 * 1024 * 1024L; // 简化版估算
        return (int) Math.ceil((staticMem + ioMemBytes * 3.0 + 50 * 1024 * 1024L) / (1024.0 * 1024.0));
    }



    @Override public int getContainerId() { return containerId; }
    @Override public Integer getVersion() { return version; }
    @Override public List<Parameter> getParameterList() { return parameterList; }
    @Override public ContainerStatus getStatus() { return status.get(); }
    @Override public int getMemoryUsage() { return runtimeMemoryUsage.get(); }
}