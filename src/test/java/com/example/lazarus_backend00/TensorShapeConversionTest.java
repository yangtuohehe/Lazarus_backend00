package com.example.lazarus_backend00;

import com.example.lazarus_backend00.domain.axis.SpaceAxisX;
import com.example.lazarus_backend00.domain.axis.SpaceAxisY;
import com.example.lazarus_backend00.domain.axis.SpaceAxisZ;
import com.example.lazarus_backend00.domain.axis.TimeAxis;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public class TensorShapeConversionTest {

    @Test
    public void testDataBlockToTensorConversion() {
        System.out.println("====== 测试开始: TSDataBlock 到 张量的转换 ======");

        // 1. 严格按照实际业务逻辑构造轴实体
        // TimeAxis: 希望 Count=1，设置 range=1.0, resolution=1.0
        TimeAxis tAxis = new TimeAxis(1.0, "hour", 1.0, "hour");

        // SpaceAxisZ: 希望 Count=1，设置 range=10.0, resolution=10.0 (10/10=1)
        SpaceAxisZ zAxis = new SpaceAxisZ(10.0, "m", 10.0, "m");

        // SpaceAxisY: 希望 Count=2，设置 range=20.0, resolution=10.0 (20/10=2)
        SpaceAxisY yAxis = new SpaceAxisY(20.0, "m", 10.0, "m");

        // SpaceAxisX: 希望 Count=2，设置 range=20.0, resolution=10.0 (20/10=2)
        SpaceAxisX xAxis = new SpaceAxisX(20.0, "m", 10.0, "m");

        Instant tOrigin = Instant.now();

        // 2. 模拟通道 1 的数据 (FeatureID = 101)
        // 空间点数 = 1(T) * 1(Z) * 2(Y) * 2(X) = 4 个数据点
        float[] tempArray = {1.1f, 1.2f, 1.3f, 1.4f};
        TSDataBlock tempBlock = new TSDataBlock.Builder()
                .featureId(101)
                .time(tOrigin, tAxis)
                .z(0.0, zAxis)
                .y(0.0, yAxis)
                .x(0.0, xAxis)
                .data(tempArray)
                .build();

        // 3. 模拟通道 2 的数据 (FeatureID = 102)
        float[] humidArray = {2.1f, 2.2f, 2.3f, 2.4f};
        TSDataBlock humidBlock = new TSDataBlock.Builder()
                .featureId(102)
                .time(tOrigin, tAxis)
                .z(0.0, zAxis)
                .y(0.0, yAxis)
                .x(0.0, xAxis)
                .data(humidArray)
                .build();

        // 4. 模拟 Orchestrator 的分组操作
        List<TSDataBlock> inputGroup0 = Arrays.asList(tempBlock, humidBlock);
        List<List<TSDataBlock>> allInputs = Arrays.asList(inputGroup0);

        System.out.println("\n[原始输入状态]");
        System.out.println("经过构造函数验证计算出的单 Block Shape: " + Arrays.toString(tempBlock.getDynamicShape()));
        System.out.println("输入端口数量: " + allInputs.size());
        System.out.println("端口0的通道(特征)数量: " + inputGroup0.size());

        // 5. 模拟进入 Container 后的转换逻辑
        simulateContainerTensorConversion(allInputs);
    }

    /**
     * 提取自 OnnxModelContainer/DjlPytorchModelContainer 的核心转换代码
     */
    private void simulateContainerTensorConversion(List<List<TSDataBlock>> inputGroups) {
        System.out.println("\n====== 容器内部张量转换处理 ======");

        for (int portIndex = 0; portIndex < inputGroups.size(); portIndex++) {
            List<TSDataBlock> group = inputGroups.get(portIndex);

            TSDataBlock template = group.get(0);
            int channelCount = group.size();
            int singleBlockSize = template.getData().length;
            int totalElements = singleBlockSize * channelCount;

            System.out.println("\n--- 处理输入端口 " + portIndex + " ---");

            // 【关键步骤 1：数据 Flatten 拼接】
            FloatBuffer buffer = FloatBuffer.allocate(totalElements);
            for (TSDataBlock block : group) {
                buffer.put(block.getData());
            }
            buffer.flip();

            float[] flattenedData = buffer.array();
            System.out.println("1. 数据拼接完成 (Flattening):");
            System.out.println("   总数据长度: " + flattenedData.length);
            System.out.println("   一维数组内容: " + Arrays.toString(flattenedData));

            // 【关键步骤 2：维度重塑 Shape Adjustment】
            long[] originalShape = template.getDynamicShape();
            long[] tensorShape = new long[originalShape.length + 1];

            tensorShape[0] = originalShape[0]; // Batch
            tensorShape[1] = originalShape[1]; // Time
            tensorShape[2] = channelCount;     // 🔥 强行插入 Channel 维度
            // Z, Y, X 向后平移
            System.arraycopy(originalShape, 2, tensorShape, 3, originalShape.length - 2);

            System.out.println("2. 维度重塑完成 (Shape Adjustment):");
            System.out.println("   原始 Shape (无通道): " + Arrays.toString(originalShape) + " -> [Batch, Time, Z, Y, X]");
            System.out.println("   张量 Shape (含通道): " + Arrays.toString(tensorShape) + " -> [Batch, Time, Channel, Z, Y, X]");

            // 验证最终匹配
            long checkVolume = 1;
            for(long dim : tensorShape) checkVolume *= dim;
            System.out.println("\n✅ 验证: 张量 Shape 容量 (" + checkVolume + ") == Flatten 数组长度 (" + flattenedData.length + ") : " + (checkVolume == flattenedData.length));
        }
    }
}