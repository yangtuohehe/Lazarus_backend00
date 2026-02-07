package com.example.lazarus_backend00.component.container;

import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.NoopTranslator;
import com.example.lazarus_backend00.dao.DynamicProcessModelDao;
import com.example.lazarus_backend00.domain.axis.Feature;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * DJL PyTorch 模型容器 (真实可用版)
 * 支持多输入组 (List<List>) 和多输出组 (List<List>)。
 * 适配单特征 TSDataBlock。
 */
public class DjlPytorchModelContainer implements ModelContainer {

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

    // DJL 核心组件
    private Model model;

    /**
     * 构造函数
     */
    public DjlPytorchModelContainer(int containerId,
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
            System.out.println(">>> [DJL-PyTorch] 容器 [" + containerId + "] 正在从数据库拉取模型数据...");

            // 1. 延迟加载：查库
            DynamicProcessModelEntity fileEntity = modelDao.selectModelFile(this.containerId);

            if (fileEntity != null) {
                tempModelArtifact = fileEntity.getModelFile();
            }

            if (tempModelArtifact == null || tempModelArtifact.length == 0) {
                throw new RuntimeException("数据库中未找到 ID=" + containerId + " 的模型数据");
            }

            System.out.println(">>> [DJL-PyTorch] 数据拉取成功，初始化 Native 引擎...");

            // 2. 创建 DJL 模型实例
            this.model = Model.newInstance("model_in_memory", "PyTorch");

            // 3. 内存加载 (IO -> Native)
            try (InputStream is = new ByteArrayInputStream(tempModelArtifact)) {
                model.load(is);
            }

            // 4. 更新内存计数 (模型大小 + 200MB 运行时开销)
            long modelSize = tempModelArtifact.length;
            int memoryMB = (int) (modelSize / (1024 * 1024)) + 200;

            this.runtimeMemoryUsage.set(memoryMB);
            this.status.set(ContainerStatus.LOADED);

            return true;

        } catch (Exception e) {
            this.status.set(ContainerStatus.FAILED);
            System.err.println("DJL 加载失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            // 显式置空临时数组，加速 GC
            tempModelArtifact = null;
        }
    }

    @Override
    public boolean unload() {
        if (this.model != null) {
            this.model.close(); // 释放 C++ 指针
            this.model = null;
        }
        this.runtimeMemoryUsage.set(0);
        this.status.set(ContainerStatus.UNLOADED);
        System.out.println("<<< [DJL-PyTorch] 容器 [" + containerId + "] 显存已释放。");
        return true;
    }

    // ================== 核心推理引擎 (List<List> 适配) ==================

    @Override
    public List<List<TSDataBlock>> run(List<List<TSDataBlock>> inputGroups) {
        // 1. 状态检查
        if (status.get() != ContainerStatus.LOADED) {
            throw new IllegalStateException("DJL 容器 [" + containerId + "] 未就绪。");
        }

        // 2. 预估推理内存
        this.runtimeMemoryUsage.addAndGet(200);

        // DJL 的资源管理核心：NDManager (类似 Try-With-Resources)
        // 所有的 NDArray 必须在此 Manager 的作用域内创建，退出时自动释放 Native 内存
        try (NDManager manager = NDManager.newBaseManager();
             Predictor<NDList, NDList> predictor = model.newPredictor(new NoopTranslator())) {

            // ==========================================
            // A. 输入对齐：List<List> -> NDList
            // ==========================================
            NDList inputNDList = new NDList();

            // 遍历每个输入张量组 (Input Group / Tensor)
            for (List<TSDataBlock> group : inputGroups) {
                if (group.isEmpty()) throw new IllegalArgumentException("输入组不能为空");

                TSDataBlock template = group.get(0);
                int channelCount = group.size();
                int singleBlockLength = template.getData().length;

                // 1. 合并数据：将多个单特征 Block 的数据拼接成一个大数组
                // 目的：构造 Multi-Channel Tensor
                float[] mergedData = new float[singleBlockLength * channelCount];

                for (int i = 0; i < channelCount; i++) {
                    float[] src = group.get(i).getData();
                    System.arraycopy(src, 0, mergedData, i * singleBlockLength, singleBlockLength);
                }

                // 2. 调整 Shape
                // 假设 TSDataBlock.Shape 是 [N, T, Z, Y, X]
                // 我们需要变成 [N, T, C, Z, Y, X]
                long[] originalShape = template.getDynamicShape();
                long[] newShape = adjustShapeForMultiChannel(originalShape, channelCount);

                // 3. 创建 NDArray
                NDArray array = manager.create(mergedData, new Shape(newShape));
                inputNDList.add(array);
            }

            // ==========================================
            // B. 执行推理
            // ==========================================
            NDList outputNDList = predictor.predict(inputNDList);

            // ==========================================
            // C. 输出对齐：NDList -> List<List>
            // ==========================================
            return parseOutputs(outputNDList, inputGroups.get(0).get(0));

        } catch (Exception e) {
            this.status.set(ContainerStatus.FAILED);
            throw new RuntimeException("DJL 推理崩溃: " + e.getMessage(), e);
        } finally {
            if (status.get() != ContainerStatus.FAILED) {
                this.runtimeMemoryUsage.addAndGet(-200);
            }
        }
    }

    // ================== 辅助方法 ==================

    /**
     * 调整 Shape 以适配多通道合并
     * 将 [Batch, Time, Z, Y, X] -> [Batch, Time, Channel, Z, Y, X]
     */
    private long[] adjustShapeForMultiChannel(long[] originalShape, int channelCount) {
        // 这是一个启发式逻辑，具体取决于你的模型 Input Layout
        // 假设 Input Layout: [Batch, Time, Channel, Z, Y, X]

        long[] newShape = new long[originalShape.length + 1];

        newShape[0] = originalShape[0]; // Batch
        newShape[1] = originalShape[1]; // Time
        newShape[2] = channelCount;     // Channel (Insert)

        System.arraycopy(originalShape, 2, newShape, 3, originalShape.length - 2);

        return newShape;
    }

    /**
     * 解析输出 NDList，还原为二维 TSDataBlock 列表
     */
    private List<List<TSDataBlock>> parseOutputs(NDList outputNDList, TSDataBlock templateBlock) {
        List<List<TSDataBlock>> allOutputs = new ArrayList<>();

        // 获取预期的输出参数定义 (按 Order 排序)
        List<Parameter> outputParams = getSortedOutputParameters();

        int ndIndex = 0;
        for (Parameter param : outputParams) {
            if (ndIndex >= outputNDList.size()) break;

            NDArray array = outputNDList.get(ndIndex++);

            // 1. 拉取数据到 Java 堆
            float[] tensorData = array.toFloatArray();

            // 2. 拆解 Channel
            List<Feature> features = param.getFeatureList();
            int channelCount = features.size();

            // 安全防御：如果模型输出大小无法被特征数整除，说明 Shape 不对
            if (tensorData.length % channelCount != 0) {
                throw new RuntimeException("模型输出大小与特征数不匹配: Output=" + param.getTensorOrder());
            }

            int singleBlockLength = tensorData.length / channelCount;
            List<TSDataBlock> groupBlocks = new ArrayList<>();

            for (int c = 0; c < channelCount; c++) {
                Feature f = features.get(c);

                // 2.1 切片
                float[] slice = new float[singleBlockLength];
                System.arraycopy(tensorData, c * singleBlockLength, slice, 0, singleBlockLength);

                // 2.2 封装
                TSDataBlock.Builder builder = new TSDataBlock.Builder();
                builder.featureId(f.getId());
                builder.data(slice);

                // 复制时空元数据
                copyMetadata(templateBlock, builder);

                groupBlocks.add(builder.build());
            }
            allOutputs.add(groupBlocks);
        }
        return allOutputs;
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