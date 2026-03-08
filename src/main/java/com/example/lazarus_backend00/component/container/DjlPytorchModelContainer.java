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
import com.example.lazarus_backend00.domain.axis.TimeAxis;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class DjlPytorchModelContainer implements ModelContainer {

    private final int containerId;
    private final Integer version;
    private final List<Parameter> parameterList;
    private final DynamicProcessModelDao modelDao;
    private final int estimatedModelSizeBytes;
    private final AtomicReference<ContainerStatus> status;
    private final AtomicInteger runtimeMemoryUsage;

    private Model model;

    public DjlPytorchModelContainer(int containerId, Integer version, List<Parameter> parameterList,
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
            if (tempModelArtifact == null || tempModelArtifact.length == 0) {
                throw new RuntimeException("No model data found for ID=" + containerId);
            }
            this.model = Model.newInstance("model_in_memory", "PyTorch");
            try (InputStream is = new ByteArrayInputStream(tempModelArtifact)) {
                model.load(is);
            }
            int memoryMB = (tempModelArtifact.length / (1024 * 1024)) + 200;
            this.runtimeMemoryUsage.set(memoryMB);
            this.status.set(ContainerStatus.LOADED);
            return true;
        } catch (Exception e) {
            this.status.set(ContainerStatus.FAILED);
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean unload() {
        if (this.model != null) {
            this.model.close();
            this.model = null;
        }
        this.runtimeMemoryUsage.set(0);
        this.status.set(ContainerStatus.UNLOADED);
        return true;
    }

    @Override
    public List<List<TSDataBlock>> run(List<List<TSDataBlock>> inputGroups) {
        if (status.get() != ContainerStatus.LOADED) throw new IllegalStateException("Container not ready.");
        this.runtimeMemoryUsage.addAndGet(200);

        try (NDManager manager = NDManager.newBaseManager();
             Predictor<NDList, NDList> predictor = model.newPredictor(new NoopTranslator())) {

            NDList inputNDList = new NDList();
            for (List<TSDataBlock> group : inputGroups) {
                TSDataBlock template = group.get(0);
                int channelCount = group.size();
                int singleBlockLength = template.getData().length;

                float[] mergedData = new float[singleBlockLength * channelCount];
                for (int i = 0; i < channelCount; i++) {
                    System.arraycopy(group.get(i).getData(), 0, mergedData, i * singleBlockLength, singleBlockLength);
                }

                long[] originalShape = template.getDynamicShape();
                long[] newShape = adjustShapeForMultiChannel(originalShape, channelCount);

                inputNDList.add(manager.create(mergedData, new Shape(newShape)));
            }

            NDList outputNDList = predictor.predict(inputNDList);
            return parseOutputs(outputNDList, inputGroups.get(0).get(0));

        } catch (Exception e) {
            this.status.set(ContainerStatus.FAILED);
            throw new RuntimeException("DJL inference failed: " + e.getMessage(), e);
        } finally {
            if (status.get() != ContainerStatus.FAILED) this.runtimeMemoryUsage.addAndGet(-200);
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

    private List<List<TSDataBlock>> parseOutputs(NDList outputNDList, TSDataBlock templateBlock) {
        List<List<TSDataBlock>> allOutputs = new ArrayList<>();
        List<Parameter> outputParams = getSortedOutputParameters();

        // ❌ 移除了原先在这里“粗暴”累加 totalInputDurationSec 的旧逻辑

        int ndIndex = 0;
        for (Parameter param : outputParams) {
            if (ndIndex >= outputNDList.size()) break;

            float[] tensorData = outputNDList.get(ndIndex++).toFloatArray();
            int channelCount = param.getFeatureList().size();

            // 🎯 核心诊断与防重叠
            int singleBlockLength = tensorData.length / channelCount;

            System.out.println("🔧 [DJL Container] Tensor Size: " + tensorData.length +
                    ", Channel Count: " + channelCount +
                    ", Elements per Block: " + singleBlockLength);

            List<TSDataBlock> groupBlocks = new ArrayList<>();

            for (int c = 0; c < channelCount; c++) {
                Feature f = param.getFeatureList().get(c);
                float[] slice = new float[singleBlockLength];

                // ⚠️ 确保精准切片
                System.arraycopy(tensorData, c * singleBlockLength, slice, 0, singleBlockLength);

                TSDataBlock.Builder builder = new TSDataBlock.Builder()
                        .featureId(f.getId())
                        .data(slice);

                // ✅ 关键更改：将整个 templateBlock 传给下游，让其利用 oTimeStep 进行绝对精度的偏移计算
                applyOutputMetadata(param, templateBlock, builder);
                groupBlocks.add(builder.build());
            }
            allOutputs.add(groupBlocks);
        }
        return allOutputs;
    }

    // ================== 替换 2：元数据注入方法 ==================
    private void applyOutputMetadata(Parameter param, TSDataBlock templateBlock, TSDataBlock.Builder builder) {

        Instant baseTOrigin = templateBlock.getTOrigin();

        // 1. 注入输出时间轴
        if (param.getTimeAxis() != null && baseTOrigin != null) {
            Instant actualOutputTime = baseTOrigin;

            if (param.getoTimeStep() != null) {
                // 🌟 新核心逻辑：输出时间 = 模型的逻辑零点(输入时间的起点) + (oTimeStep偏移步数 * 输出时间轴的物理分辨率步长)
                long offsetSeconds = param.getoTimeStep() * getAxisResolutionInSeconds(param.getTimeAxis());
                actualOutputTime = baseTOrigin.plusSeconds(offsetSeconds);
            } else {
                // 🔙 兼容回退逻辑：如果数据库中该参数没配 oTimeStep，默认假定它紧接在输入数据的结尾
                TimeAxis inputTAxis = templateBlock.getTAxis();
                if (inputTAxis != null && inputTAxis.getCount() != null) {
                    long totalInputDurationSec = inputTAxis.getCount() * getAxisResolutionInSeconds(inputTAxis);
                    actualOutputTime = baseTOrigin.plusSeconds(totalInputDurationSec);
                }
            }

            builder.time(actualOutputTime, param.getTimeAxis());
        }

        // 2. 注入输出空间轴 (原有代码保持不变)
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

    @Override public int getContainerId() { return containerId; }
    @Override public Integer getVersion() { return version; }
    @Override public List<Parameter> getParameterList() { return parameterList; }
    @Override public ContainerStatus getStatus() { return status.get(); }
    @Override public int getMemoryUsage() { return runtimeMemoryUsage.get(); }
}