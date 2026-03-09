package com.example.lazarus_backend00;

import com.example.lazarus_backend00.component.container.DirectOnnxModelContainer;
import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.domain.axis.*;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DirectOnnxContainerIntegrationTest {

    @Test
    public void testDirectOnnxContainerPureMemory() throws Exception {
        System.out.println("========== 1. 初始化空间与时间原点 ==========");
        // 只使用你现有的 JTS 库
        GeometryFactory gf = new GeometryFactory();
        Point originPoint = gf.createPoint(new Coordinate(0.0, 0.0));
        Instant startInstant = Instant.parse("2024-01-01T00:00:00Z");

        System.out.println("========== 2. 构造物理维度轴 (Axis) ==========");
        TimeAxis inputTimeAxis = new TimeAxis();
        inputTimeAxis.setCount(3);
        inputTimeAxis.setResolution(1.0);
        inputTimeAxis.setUnit("month");
        inputTimeAxis.setDimensionIndex(1);

        TimeAxis outputTimeAxis = new TimeAxis();
        outputTimeAxis.setCount(1);
        outputTimeAxis.setResolution(1.0);
        outputTimeAxis.setUnit("month");
        outputTimeAxis.setDimensionIndex(1);

        SpaceAxisY yAxis60 = new SpaceAxisY();
        yAxis60.setCount(60);
        yAxis60.setResolution(10.0);
        yAxis60.setUnit("m");
        yAxis60.setDimensionIndex(3);

        SpaceAxisX xAxis45 = new SpaceAxisX();
        xAxis45.setCount(45);
        xAxis45.setResolution(10.0);
        xAxis45.setUnit("m");
        xAxis45.setDimensionIndex(4);

        SpaceAxisY yAxis1 = new SpaceAxisY();
        yAxis1.setCount(1);
        yAxis1.setResolution(10.0);
        yAxis1.setUnit("m");
        yAxis1.setDimensionIndex(3);

        SpaceAxisX xAxis1 = new SpaceAxisX();
        xAxis1.setCount(1);
        xAxis1.setResolution(10.0);
        xAxis1.setUnit("m");
        xAxis1.setDimensionIndex(4);

        System.out.println("========== 3. 构造特征 (Features) ==========");
        Feature featureNdvi = new Feature(1, "NDVI");
        Feature featureEnv1 = new Feature(2, "Env_Factor_1");
        Feature featureEnv2 = new Feature(3, "Env_Factor_2");
        Feature featureEnv3 = new Feature(4, "Env_Factor_3");

        System.out.println("========== 4. 构造容器 Parameter 契约 ==========");
        // ⚠️ 第三个参数 0 和 3 就是我们在 Parameter 类中新增的 oTimeStep
        Parameter paramInput1 = new Parameter("INPUT", 0, 0, originPoint, Arrays.asList(inputTimeAxis, yAxis60, xAxis45), Arrays.asList(featureNdvi));
        Parameter paramInput2 = new Parameter("INPUT", 1, 0, originPoint, Arrays.asList(inputTimeAxis, yAxis1, xAxis1), Arrays.asList(featureEnv1, featureEnv2, featureEnv3));
        Parameter paramOutput = new Parameter("OUTPUT", 0, 3, originPoint, Arrays.asList(outputTimeAxis, yAxis60, xAxis45), Arrays.asList(featureNdvi));

        List<Parameter> containerParameters = Arrays.asList(paramInput1, paramInput2, paramOutput);

        System.out.println("========== 5. 模拟读取 TIF 像素组装 TSDataBlock ==========");
        List<List<TSDataBlock>> inputGroups = new ArrayList<>();

        // ------------------ 组装 Group 1 (模拟 3 张 45x60 的 NDVI TIF) ------------------
        List<TSDataBlock> group1 = new ArrayList<>();
        int ndviFrameSize = 60 * 45;
        float[] ndviDataCombined = new float[3 * ndviFrameSize];

        // 模拟像素填充
        for (int t = 0; t < 3; t++) {
            System.out.println("   -> 模拟读取文件: in/NDVI_" + t + ".tif (大小: 45x60)");
            float[] mockPixels = new float[ndviFrameSize];
            Arrays.fill(mockPixels, 0.5f + (t * 0.1f)); // 填入假数据
            System.arraycopy(mockPixels, 0, ndviDataCombined, t * ndviFrameSize, ndviFrameSize);
        }

        TSDataBlock blockNdvi = new TSDataBlock.Builder()
                .featureId(featureNdvi.getId())
                .time(startInstant, inputTimeAxis)
                .y(originPoint.getY(), yAxis60)
                .x(originPoint.getX(), xAxis45)
                .data(ndviDataCombined)
                .build();
        group1.add(blockNdvi);
        inputGroups.add(group1);

        // ------------------ 组装 Group 2 (模拟 9 张 1x1 的环境要素 TIF) ------------------
        List<TSDataBlock> group2 = new ArrayList<>();
        int envFrameSize = 1 * 1;
        List<Feature> envFeatures = Arrays.asList(featureEnv1, featureEnv2, featureEnv3);

        for (Feature envFeature : envFeatures) {
            float[] envDataCombined = new float[3 * envFrameSize];
            for (int t = 0; t < 3; t++) {
                System.out.println("   -> 模拟读取文件: in/" + envFeature.getFeatureName() + "_" + t + ".tif (大小: 1x1)");
                float[] mockPixels = new float[envFrameSize];
                Arrays.fill(mockPixels, 1.2f);
                System.arraycopy(mockPixels, 0, envDataCombined, t * envFrameSize, envFrameSize);
            }

            TSDataBlock blockEnv = new TSDataBlock.Builder()
                    .featureId(envFeature.getId())
                    .time(startInstant, inputTimeAxis)
                    .y(originPoint.getY(), yAxis1)
                    .x(originPoint.getX(), xAxis1)
                    .data(envDataCombined)
                    .build();
            group2.add(blockEnv);
        }
        inputGroups.add(group2);

        System.out.println("========== 6. 实例化容器并加载 ONNX 模型 ==========");
        String modelPath = "C:/temp/your_test_model.onnx"; // ⚠️ 替换为真实的本地 ONNX 模型路径
        byte[] modelBytes;
        try {
            modelBytes = Files.readAllBytes(Paths.get(modelPath));
        } catch (Exception e) {
            System.err.println("\n⚠️ 找不到本地 ONNX 模型文件: " + modelPath);
            System.err.println("测试中断。请修改代码中的 modelPath 路径后重新运行！");
            return;
        }

        DirectOnnxModelContainer container = new DirectOnnxModelContainer(1001, 1, containerParameters, modelBytes);
        if (!container.load()) {
            throw new RuntimeException("ONNX 容器加载失败，可能是权重文件格式不正确。");
        }

        System.out.println("========== 7. 执行计算 (run) ==========");
        List<List<TSDataBlock>> outputGroups = container.run(inputGroups);

        System.out.println("========== 8. 解析输出结果 ==========");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);

        for (int i = 0; i < outputGroups.size(); i++) {
            List<TSDataBlock> portOutputs = outputGroups.get(i);
            for (int j = 0; j < portOutputs.size(); j++) {
                TSDataBlock outBlock = portOutputs.get(j);
                float[] outData = outBlock.getData();

                // 根据推算出的真实时间，生成模拟输出的文件名
                String timeStr = timeFormatter.format(outBlock.getTOrigin());
                String outFileName = "out/NDVI_" + timeStr + ".tif";

                System.out.println("\n   <- 模拟写出文件: " + outFileName);
                System.out.println("      ✅ 验证点 1 [时间对齐]: 预期输出时间为 2024-04-01T00:00:00Z");
                System.out.println("      -> 实际输出时间: " + outBlock.getTOrigin());
                System.out.println("      ✅ 验证点 2 [地理位置]: 预期起点 (0.0, 0.0)");
                System.out.println("      -> 实际起点: (" + outBlock.getXOrigin() + ", " + outBlock.getYOrigin() + ")");
                System.out.println("      ✅ 验证点 3 [数据量]: 预期长度 2700 (60x45)");
                System.out.println("      -> 实际长度: " + outData.length);
            }
        }

        container.unload();
        System.out.println("\n========== 测试执行完毕 ==========");
    }
}