package com.example.lazarus_backend00.component.container;

import ai.onnxruntime.*;
import com.example.lazarus_backend00.domain.axis.Feature;
import com.example.lazarus_backend00.domain.axis.TimeAxis;
import com.example.lazarus_backend00.domain.data.TSDataBlock;

import java.nio.FloatBuffer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 直接加载版 ONNX 容器 (Direct Memory Container) - 真实可用版
 * 适用场景：临时模型、外部二进制流直接注入。
 * 适配：单特征 TSDataBlock + List<List> 接口。
 */
public class DirectOnnxModelContainer implements ModelContainer {

    // ================== 元数据 ==================
    private final int containerId;
    private final Integer version;
    private final List<Parameter> parameterList;

    // 直接持有二进制数据 (Java Heap)
    private final byte[] modelArtifact;

    // ================== 运行时状态 ==================
    private final AtomicReference<ContainerStatus> status;
    private final AtomicInteger runtimeMemoryUsage;

    // ONNX Runtime 组件
    private OrtEnvironment env;
    private OrtSession session;

    /**
     * 构造函数
     * @param modelArtifact 必须在构造时直接注入完整的二进制数据
     */
    public DirectOnnxModelContainer(int containerId,
                                    Integer version,
                                    List<Parameter> parameterList,
                                    byte[] modelArtifact) {
        this.containerId = containerId;
        this.version = version;
        this.parameterList = parameterList;
        this.modelArtifact = modelArtifact;

        this.status = new AtomicReference<>(ContainerStatus.CREATED);

        // 初始内存：包含了 Java 堆中的 byte[] 大小
        int artifactSizeMB = (modelArtifact != null ? modelArtifact.length : 0) / (1024 * 1024);
        this.runtimeMemoryUsage = new AtomicInteger(artifactSizeMB);
    }

    // ================== 生命周期管理 (保持不变) ==================

    @Override
    public boolean load() {
        if (status.get() == ContainerStatus.LOADED) return true;

        try {
            System.out.println(">>> [Direct-ONNX] 正在加载内存中的二进制模型 [" + containerId + "]...");

            if (this.modelArtifact == null || this.modelArtifact.length == 0) {
                throw new IllegalStateException("容器内的模型二进制数据为空");
            }

            // 1. 初始化环境
            this.env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            // options.addCUDA(0); // 如需 GPU 支持

            // 2. 直接使用成员变量加载 Session (Java Heap -> Native Memory)
            this.session = env.createSession(this.modelArtifact, options);

            // 3. 更新内存占用 (Artifact Size + Native Size + Runtime Overhead)
            int nativeSizeMB = (modelArtifact.length / (1024 * 1024)) + 100;
            this.runtimeMemoryUsage.addAndGet(nativeSizeMB);

            this.status.set(ContainerStatus.LOADED);
            return true;

        } catch (OrtException e) {
            this.status.set(ContainerStatus.FAILED);
            System.err.println("ONNX 引擎初始化失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean unload() {
        try {
            if (session != null) {
                session.close(); // 释放 Native 内存
                session = null;
            }
            if (env != null) {
                env.close();
                env = null;
            }

            // 卸载后，内存恢复为仅剩 modelArtifact 的大小 (Java Heap)
            int artifactSizeMB = (modelArtifact != null ? modelArtifact.length : 0) / (1024 * 1024);
            this.runtimeMemoryUsage.set(artifactSizeMB);

            this.status.set(ContainerStatus.UNLOADED);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ================== 核心推理引擎 (List<List> 适配) ==================

    @Override
    public List<List<TSDataBlock>> run(List<List<TSDataBlock>> inputGroups) {
        // 1. 状态检查
        if (status.get() != ContainerStatus.LOADED) {
            throw new IllegalStateException("Direct 容器 [" + containerId + "] 未就绪，当前状态: " + status.get());
        }

        // 2. 预估运行时峰值内存
        int estimatedCost = 50;
        this.runtimeMemoryUsage.addAndGet(estimatedCost);

        Map<String, OnnxTensor> inputTensorMap = new HashMap<>();

        try {
            // 3. 校验输入数量
            if (session.getNumInputs() != inputGroups.size()) {
                throw new IllegalArgumentException("输入组数不匹配: 模型需要 " + session.getNumInputs()
                        + " 个，实际传入 " + inputGroups.size() + " 个");
            }

            // 4. 构建 Input Tensors
            Iterator<String> inputNameIter = session.getInputNames().iterator();

            for (List<TSDataBlock> group : inputGroups) {
                String inputNodeName = inputNameIter.next();
                if (group.isEmpty()) throw new IllegalArgumentException("输入组不能为空: " + inputNodeName);

                TSDataBlock template = group.get(0);
                int singleBlockSize = template.getData().length;
                int totalElements = singleBlockSize * group.size();

                // 4.1 创建 Buffer 并填充 (Channel 合并)
                FloatBuffer buffer = FloatBuffer.allocate(totalElements);
                for (TSDataBlock block : group) {
                    buffer.put(block.getData());
                }
                buffer.flip();

                // 4.2 计算 Shape [Batch, Time, Channel, Z, Y, X]
                long[] templateShape = template.getDynamicShape();
                long[] mergedShape = adjustShapeForMultiChannel(templateShape, group.size());

                // 4.3 创建 Tensor
                OnnxTensor tensor = OnnxTensor.createTensor(env, buffer, mergedShape);
                inputTensorMap.put(inputNodeName, tensor);
            }

            // 5. 执行推理
            try (OrtSession.Result results = session.run(inputTensorMap)) {
                // 6. 处理输出
                return parseOutputs(results, inputGroups.get(0).get(0));
            }

        } catch (OrtException e) {
            this.status.set(ContainerStatus.FAILED);
            throw new RuntimeException("ONNX 推理失败: " + e.getMessage(), e);
        } finally {
            // 7. 资源清理
            for (OnnxTensor tensor : inputTensorMap.values()) {
                if (tensor != null) tensor.close();
            }
            this.runtimeMemoryUsage.addAndGet(-estimatedCost);
        }
    }

    // ================== 辅助方法 ==================

    /**
     * 调整 Shape 以适配多通道合并
     * 将 [Batch, Time, Z, Y, X] -> [Batch, Time, Channel, Z, Y, X]
     */
    private long[] adjustShapeForMultiChannel(long[] originalShape, int channelCount) {
        // 假设 Input Layout: [Batch, Time, Channel, Z, Y, X]
        // 原 Shape 长度为 5 (Batch, Time, Z, Y, X)

        long[] newShape = new long[originalShape.length + 1];

        newShape[0] = originalShape[0]; // Batch
        newShape[1] = originalShape[1]; // Time
        newShape[2] = channelCount;     // Channel

        System.arraycopy(originalShape, 2, newShape, 3, originalShape.length - 2);

        return newShape;
    }

    /**
     * 解析输出结果，还原为 List<List<TSDataBlock>>
     */
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

    // ================== 核心修复：新增的两个元数据推演与组装辅助方法 ==================

    /**
     * 将解析出的时间原点和输出参数中定义的空间轴，强制注入到 DataBlock 中
     */
    private void applyOutputMetadata(Parameter param, Instant outputTOrigin, TSDataBlock.Builder builder) {
        // 1. 注入输出时间轴
        if (param.getTimeAxis() != null && outputTOrigin != null) {
            builder.time(outputTOrigin, param.getTimeAxis());
        }

        // 2. 注入输出空间轴
        if (param.getAxisList() != null) {
            for (com.example.lazarus_backend00.domain.axis.Axis axis : param.getAxisList()) {
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

    /**
     * 将时间轴的单位统一转换为秒，用于计算时间偏移量
     */
    private long getAxisResolutionInSeconds(com.example.lazarus_backend00.domain.axis.TimeAxis tAxis) {
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

    private void copyMetadata(TSDataBlock template, TSDataBlock.Builder builder) {
        if (template.getTAxis() != null) {
            if (template.getBatchSize() > 1) {
                builder.batchTime(template.getBatchTOrigins(), template.getTAxis());
            } else {
                builder.time(template.getTOrigin(), template.getTAxis());
            }
        }
        if (template.getZAxis() != null) builder.z(template.getZOrigin(), template.getZAxis());
        if (template.getYAxis() != null) builder.y(template.getYOrigin(), template.getYAxis());
        if (template.getXAxis() != null) builder.x(template.getXOrigin(), template.getXAxis());
    }

    // ================== Getters ==================
    @Override public int getContainerId() { return containerId; }
    @Override public Integer getVersion() { return version; }
    @Override public List<Parameter> getParameterList() { return parameterList; }
    @Override public ContainerStatus getStatus() { return status.get(); }
    @Override public int getMemoryUsage() { return runtimeMemoryUsage.get(); }
}