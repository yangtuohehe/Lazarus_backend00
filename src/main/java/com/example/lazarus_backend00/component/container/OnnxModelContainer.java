package com.example.lazarus_backend00.component.container;

import ai.onnxruntime.*;
import com.example.lazarus_backend00.dao.DynamicProcessModelDao;
import com.example.lazarus_backend00.domain.axis.Axis;
import com.example.lazarus_backend00.domain.axis.Feature;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;

import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * ONNX Runtime 模型容器 (真实可用版)
 * 支持多输入组 (List<List>) 和多输出组 (List<List>)。
 */
public class OnnxModelContainer implements ModelContainer {

    // ================== 元数据 ==================
    private final int containerId;
    private final Integer version;
    private final List<Parameter> parameterList;

    // ================== 外部依赖 ==================
    private final DynamicProcessModelDao modelDao;
    private final int estimatedModelSizeBytes;

    // ================== 运行时状态 ==================
    private final AtomicReference<ContainerStatus> status;
    private final AtomicInteger runtimeMemoryUsage;

    // ONNX Runtime 核心组件
    private OrtEnvironment env;
    private OrtSession session;

    /**
     * 构造函数
     */
    public OnnxModelContainer(int containerId,
                              Integer version,
                              List<Parameter> parameterList,
                              DynamicProcessModelDao modelDao,
                              int modelSizeBytes) {
        this.containerId = containerId;
        this.version = version;
        this.parameterList = parameterList;
        this.modelDao = modelDao;
        this.estimatedModelSizeBytes = modelSizeBytes;

        this.status = new AtomicReference<>(ContainerStatus.CREATED);
        this.runtimeMemoryUsage = new AtomicInteger(0);
    }

    // ================== 生命周期管理 (保持不变) ==================

    @Override
    public boolean load() {
        if (status.get() == ContainerStatus.LOADED) return true;

        byte[] tempModelArtifact = null;
        try {
            System.out.println(">>> [ONNX] 容器 [" + containerId + "] 开始加载 (查库)...");

            // 1. 查库获取模型二进制
            DynamicProcessModelEntity fileEntity = modelDao.selectModelFile(this.containerId);

            if (fileEntity != null) {
                tempModelArtifact = fileEntity.getModelFile();
            }

            if (tempModelArtifact == null || tempModelArtifact.length == 0) {
                throw new RuntimeException("数据库中未找到 ID=" + containerId + " 的模型二进制数据");
            }

            long artifactSizeBytes = tempModelArtifact.length;
            System.out.println(">>> [ONNX] 文件拉取成功 (" + (artifactSizeBytes / 1024) + " KB)，初始化引擎...");

            // 2. 初始化环境
            this.env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            // options.addCUDA(0); // 显卡支持

            // 3. 创建会话 (加载到 Native 内存)
            this.session = env.createSession(tempModelArtifact, options);

            // 4. 🔥 核心逻辑：动态估算内存占用
            // 此时我们既有 ParameterList (IO形状)，又有 artifactSizeBytes (权重大小)
            int estimatedMB = calculateMemoryUsage(artifactSizeBytes);

            // 更新内存计数
            this.runtimeMemoryUsage.set(estimatedMB);

            System.out.println(">>> [ONNX] 容器 [" + containerId + "] 加载完成。预估内存占用: " + estimatedMB + " MB");

            this.status.set(ContainerStatus.LOADED);
            return true;

        } catch (OrtException e) {
            this.status.set(ContainerStatus.FAILED);
            System.err.println("ONNX 引擎初始化失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            this.status.set(ContainerStatus.FAILED);
            System.err.println("数据库加载失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            // 显式置空临时数组，加速 GC (数据已拷贝入 C++ 堆)
            tempModelArtifact = null;
        }
    }

    @Override
    public boolean unload() {
        try {
            if (session != null) {
                session.close(); // 释放 C++ 资源
                session = null;
            }
            if (env != null) {
                env.close();
                env = null;
            }

            // 🔥 核心逻辑：卸载后内存计数归零
            this.runtimeMemoryUsage.set(0);

            this.status.set(ContainerStatus.UNLOADED);
            System.out.println("<<< [ONNX] 容器 [" + containerId + "] 资源已释放，内存归零。");
            return true;
        } catch (Exception e) {
            System.err.println("卸载异常: " + e.getMessage());
            return false;
        }
    }

    // ================== 核心推理引擎 (二维列表适配) ==================

    @Override
    public List<List<TSDataBlock>> run(List<List<TSDataBlock>> inputGroups) {
        // 1. 状态检查
        if (status.get() != ContainerStatus.LOADED) {
            throw new IllegalStateException("ONNX 容器 [" + containerId + "] 未就绪，当前状态: " + status.get());
        }

        // 2. 临时增加内存计数
        int estimatedCost = 50;
        this.runtimeMemoryUsage.addAndGet(estimatedCost);

        // 准备输入 Tensor Map
        Map<String, OnnxTensor> inputTensorMap = new HashMap<>();

        try {
            // 3. 校验输入数量
            if (session.getNumInputs() != inputGroups.size()) {
                throw new IllegalArgumentException("输入组数不匹配: 模型定义需要 " + session.getNumInputs()
                        + " 个输入张量，实际传入 " + inputGroups.size() + " 组");
            }

            // 4. 遍历输入组，构建 Tensor
            Iterator<String> inputNameIter = session.getInputNames().iterator();

            // 外层循环：遍历模型的一个个输入张量接口 (Input Port)
            for (List<TSDataBlock> group : inputGroups) {
                String inputNodeName = inputNameIter.next();

                // 4.1. 维度检查与合并
                // 假设这一组 Block 都要拼接到同一个 Tensor (Batch, Time, Channel, H, W)
                // 这里我们做简化假设：channel = group.size()，所有 Block 的时空维度一致

                if (group.isEmpty()) throw new IllegalArgumentException("输入组不能为空: " + inputNodeName);

                TSDataBlock template = group.get(0);
                int singleBlockSize = template.getData().length; // 单个特征的数据量
                int totalElements = singleBlockSize * group.size();

                // 4.2. 创建 Buffer 并填充数据
                FloatBuffer buffer = FloatBuffer.allocate(totalElements);

                // 按顺序放入每个特征的数据 (Channel 维度拼接)
                for (TSDataBlock block : group) {
                    buffer.put(block.getData());
                }
                buffer.flip(); // 准备读取

                // 4.3. 计算合并后的 Shape
                // 注意：这里需要根据模型实际期望的维度顺序来调整
                // 通常多通道张量形状为 [Batch, Time, Channel, Z, Y, X]
                // 原始单特征 Shape: [Batch, Time, 1, Z, Y, X] (假设)
                long[] templateShape = template.getDynamicShape();
                long[] mergedShape = adjustShapeForMultiChannel(templateShape, group.size());

                // 4.4. 创建 Tensor
                OnnxTensor tensor = OnnxTensor.createTensor(env, buffer, mergedShape);
                inputTensorMap.put(inputNodeName, tensor);
            }

            // 5. 执行推理 (Run)
            try (OrtSession.Result results = session.run(inputTensorMap)) {

                // 6. 处理输出 (反向拆解)
                return parseOutputs(results, inputGroups.get(0).get(0));
            }

        } catch (OrtException e) {
            this.status.set(ContainerStatus.FAILED);
            throw new RuntimeException("ONNX 推理错误: " + e.getMessage(), e);
        } finally {
            // 7. 资源清理
            for (OnnxTensor tensor : inputTensorMap.values()) {
                if (tensor != null) tensor.close();
            }
            this.runtimeMemoryUsage.addAndGet(-estimatedCost);
        }
    }

    // ================== 辅助逻辑 ==================

    /**
     * 调整 Shape 以适配多通道合并
     * 假设原始 Shape (来自 TSDataBlock.getDynamicShape) 已经包含 Batch, Time, Space
     * 我们需要插入一个 Channel 维度，或者如果已有 Channel=1 则修改它。
     * * 这里简化处理：我们假设 TSDataBlock 的 getDynamicShape 返回 [Batch, Time, Z, Y, X]
     * 我们将其改为 [Batch, Time, Channel, Z, Y, X]
     */
    private long[] adjustShapeForMultiChannel(long[] originalShape, int channelCount) {
        // 这是一个启发式逻辑，具体取决于你的模型 Input Layout (NCHW vs NHWC)
        // 假设模型接受: [Batch, Time, Channel, Z, Y, X]

        long[] newShape = new long[originalShape.length + 1];

        // Copy Batch, Time (假设前两维)
        newShape[0] = originalShape[0]; // Batch
        newShape[1] = originalShape[1]; // Time

        // Insert Channel
        newShape[2] = channelCount;

        // Copy Space (Z, Y, X)
        System.arraycopy(originalShape, 2, newShape, 3, originalShape.length - 2);

        return newShape;
    }

    /**
     * 解析输出结果，还原为 List<List<TSDataBlock>>
     */
    private List<List<TSDataBlock>> parseOutputs(OrtSession.Result results, TSDataBlock templateBlock) {
        List<List<TSDataBlock>> allOutputs = new ArrayList<>();

        // 获取输出参数定义 (按 Order 排序)
        List<Parameter> outputParams = getSortedOutputParameters();

        Iterator<Map.Entry<String, OnnxValue>> resultIter = results.iterator();

        // 遍历每一个输出张量 (Output Tensor)
        for (Parameter param : outputParams) {
            if (!resultIter.hasNext()) break;

            OnnxValue value = resultIter.next().getValue();
            if (!(value instanceof OnnxTensor)) continue;

            try {
                // 1. 获取张量数据
                float[] tensorData = ((OnnxTensor) value).getFloatBuffer().array();

                // 2. 拆解 Channel
                // 假设输出张量也是 [Batch, Time, Channel, Z, Y, X]
                // 我们需要把它拆回 param.featureList.size() 个单特征 Block

                List<Feature> features = param.getFeatureList();
                int channelCount = features.size();
                int singleBlockLength = tensorData.length / channelCount;

                List<TSDataBlock> portOutputs = new ArrayList<>();

                for (int c = 0; c < channelCount; c++) {
                    Feature f = features.get(c);

                    // 2.1 提取分片数据
                    float[] slice = new float[singleBlockLength];
                    System.arraycopy(tensorData, c * singleBlockLength, slice, 0, singleBlockLength);

                    // 2.2 封装为 Block
                    TSDataBlock.Builder builder = new TSDataBlock.Builder();
                    builder.featureId(f.getId());
                    builder.data(slice);

                    // 复制时空元数据 (Batch, Time, Axis)
                    if (templateBlock.getTAxis() != null) {
                        if (templateBlock.getBatchSize() > 1) {
                            builder.batchTime(templateBlock.getBatchTOrigins(), templateBlock.getTAxis());
                        } else {
                            builder.time(templateBlock.getTOrigin(), templateBlock.getTAxis());
                        }
                    }
                    if (templateBlock.getZAxis() != null) builder.z(templateBlock.getZOrigin(), templateBlock.getZAxis());
                    if (templateBlock.getYAxis() != null) builder.y(templateBlock.getYOrigin(), templateBlock.getYAxis());
                    if (templateBlock.getXAxis() != null) builder.x(templateBlock.getXOrigin(), templateBlock.getXAxis());

                    portOutputs.add(builder.build());
                }

                allOutputs.add(portOutputs);

            } catch (Exception e) {
                throw new RuntimeException("解析输出张量失败: " + param.getTensorOrder(), e);
            }
        }

        return allOutputs;
    }

    /**
     * 获取排序后的输出参数定义
     */
    private List<Parameter> getSortedOutputParameters() {
        return parameterList.stream()
                .filter(p -> "OUTPUT".equalsIgnoreCase(p.getIoType()))
                .sorted(Comparator.comparingInt(Parameter::getTensorOrder))
                .collect(Collectors.toList());
    }

    // ================== Getters ==================
    @Override public int getContainerId() { return containerId; }
    @Override public Integer getVersion() { return version; }
    @Override public List<Parameter> getParameterList() { return parameterList; }
    @Override public ContainerStatus getStatus() { return status.get(); }
    @Override public int getMemoryUsage() { return runtimeMemoryUsage.get(); }

    // ================== 🧠 私有内存估算方法 ==================

    /**
     * 根据权重文件大小和参数形状，估算运行时内存
     */
    private int calculateMemoryUsage(long weightSizeBytes) {
        // 1. 静态权重 (Native Memory)
        // 经验值：加载后通常会有一些内存对齐和结构开销，乘 1.2
        long staticMem = (long) (weightSizeBytes * 1.2);

        // 2. 动态 IO 张量体积 (Input + Output)
        long totalTensorElements = 0;
        if (this.parameterList != null) {
            for (Parameter p : this.parameterList) {
                totalTensorElements += calculateShapeVolume(p);
            }
        }
        // Float32 = 4 bytes
        long ioMemBytes = totalTensorElements * 4;

        // 3. 中间激活值 (Activation Maps) 膨胀系数
        // 深度网络运行时，中间层的显存占用通常是输入输出的数倍。
        // 这里取保守值 3.0
        long dynamicMem = (long) (ioMemBytes * 3.0);

        // 4. 框架基础开销 (约 50MB)
        long baseOverhead = 50 * 1024 * 1024L;

        long totalBytes = staticMem + dynamicMem + baseOverhead;
        int totalMB = (int) Math.ceil(totalBytes / (1024.0 * 1024.0));

        // 防御性保底
        return Math.max(totalMB, 50);
    }

    /**
     * 计算单个参数的时空体积 (Grid Points)
     */
    private long calculateShapeVolume(Parameter p) {
        long volume = 1;

        // 1. 特征通道数
        if (p.getFeatureList() != null && !p.getFeatureList().isEmpty()) {
            volume *= p.getFeatureList().size();
        }

        // 2. 时空维度 (Time, Z, Y, X)
        if (p.getAxisList() != null) {
            for (Axis axis : p.getAxisList()) {
                if (axis.getCount() != null && axis.getCount() > 0) {
                    volume *= axis.getCount();
                }
            }
        }
        // 默认 Batch Size = 1
        return volume;
    }
}