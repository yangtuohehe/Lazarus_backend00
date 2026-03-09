package com.example.lazarus_backend00;

import com.example.lazarus_backend00.component.container.ModelContainer;
import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.domain.axis.Axis;
import com.example.lazarus_backend00.domain.axis.SpaceAxisX;
import com.example.lazarus_backend00.domain.axis.SpaceAxisY;
import com.example.lazarus_backend00.domain.axis.SpaceAxisZ;
import com.example.lazarus_backend00.domain.axis.TimeAxis;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.service.ModelContainerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SpringBootTest
public class OnnxDirectProbeTest {

    @Autowired
    private ModelContainerProvider containerProvider;

    @Test
    public void probeModelId2Output() {
        int targetModelId = 3;
        System.out.println("\n🚀 ========================================================");
        System.out.println("🚀 开始直接内存探测模型 ID: " + targetModelId);
        System.out.println("🚀 ========================================================\n");

        // 1. 从数据库重建模型容器
        ModelContainer container;
        try {
            container = containerProvider.reconstructContainer(targetModelId);
            System.out.println("✅ 数据库模型组装成功！");
        } catch (Exception e) {
            System.err.println("❌ 模型组装失败: " + e.getMessage());
            return;
        }

        // 2. 加载物理 ONNX 文件
        boolean isLoaded = container.load();
        System.out.println("📦 物理 ONNX 文件加载状态: " + isLoaded);
        if (!isLoaded) {
            System.err.println("❌ ONNX 文件加载失败，请检查模型文件是否损坏！");
            return;
        }

        try {
            System.out.println("🏃‍♂️ 开始根据数据库参数，动态构造带有完整轴信息的内存 TSDataBlock...");
            List<List<TSDataBlock>> inputGroups = new ArrayList<>();
            Instant baseTime = Instant.parse("2026-01-01T00:00:00Z");

            // 3. 动态遍历输入参数，生成带有时空外壳的 DataBlock
            for (Parameter param : container.getParameterList()) {
                if (!"INPUT".equalsIgnoreCase(param.getIoType())) continue;

                int channelCount = param.getFeatureList().size();

                // 计算单通道总像素数，并提取轴
                int elementsPerChannel = 1;
                TimeAxis tAxis = null;
                SpaceAxisZ zAxis = null;
                SpaceAxisY yAxis = null;
                SpaceAxisX xAxis = null;

                if (param.getAxisList() != null) {
                    for (Axis axis : param.getAxisList()) {
                        if (axis.getCount() != null && axis.getCount() > 0) {
                            elementsPerChannel *= axis.getCount();
                        }
                        if (axis instanceof TimeAxis) tAxis = (TimeAxis) axis;
                        else if (axis instanceof SpaceAxisZ) zAxis = (SpaceAxisZ) axis;
                        else if (axis instanceof SpaceAxisY) yAxis = (SpaceAxisY) axis;
                        else if (axis instanceof SpaceAxisX) xAxis = (SpaceAxisX) axis;
                    }
                }

                System.out.println("   -> 构造输入端口: " + channelCount + " 个通道, 每通道 " + elementsPerChannel + " 个元素.");

                List<TSDataBlock> portBlocks = new ArrayList<>();
                for (int c = 0; c < channelCount; c++) {
                    float[] dummyData = new float[elementsPerChannel];
                    Arrays.fill(dummyData, 1.0f); // 随便塞点数进去，防止出现 NaN 错误

                    TSDataBlock.Builder builder = new TSDataBlock.Builder()
                            .featureId(param.getFeatureList().get(c).getId())
                            .data(dummyData);

                    // 🎯 核心修复：必须克隆轴对象！绝不能直接传入 param 里的引用！
                    if (tAxis != null) {
                        TimeAxis cloneT = new TimeAxis(tAxis.getResolution(), tAxis.getUnit(), tAxis.getResolution(), tAxis.getUnit());
                        cloneT.setCount(tAxis.getCount());
                        builder.time(baseTime, cloneT);
                    }
                    if (zAxis != null) {
                        SpaceAxisZ cloneZ = new SpaceAxisZ(zAxis.getResolution(), zAxis.getUnit(), zAxis.getResolution(), zAxis.getUnit());
                        cloneZ.setCount(zAxis.getCount());
                        builder.z(0.0, cloneZ);
                    }
                    if (yAxis != null) {
                        SpaceAxisY cloneY = new SpaceAxisY(yAxis.getResolution(), yAxis.getUnit(), yAxis.getResolution(), yAxis.getUnit());
                        cloneY.setCount(yAxis.getCount());
                        builder.y(0.0, cloneY);
                    }
                    if (xAxis != null) {
                        SpaceAxisX cloneX = new SpaceAxisX(xAxis.getResolution(), xAxis.getUnit(), xAxis.getResolution(), xAxis.getUnit());
                        cloneX.setCount(xAxis.getCount());
                        builder.x(0.0, cloneX);
                    }

                    portBlocks.add(builder.build());
                }
                inputGroups.add(portBlocks);
            }

            // 4. 执行推理！
            System.out.println("\n🔥 启动模型推理 (run) ...");
            List<List<TSDataBlock>> outputs = container.run(inputGroups);

            // 5. 打印验尸解剖报告
            System.out.println("\n🎉 模型运行成功！下面是最真实的输出解剖报告：");
            System.out.println("👉 顶级输出集合数量 (代表输出了几个张量/端口): " + outputs.size());
            System.out.println("---------------------------------------------------------");

            for (int i = 0; i < outputs.size(); i++) {
                List<TSDataBlock> portOutput = outputs.get(i);
                System.out.println("   ▶️ 第 [" + i + "] 个输出端口，解析出的 DataBlock (通道) 数量: " + portOutput.size());

                for (int j = 0; j < portOutput.size(); j++) {
                    TSDataBlock block = portOutput.get(j);
                    int dataLength = (block.getData() != null) ? block.getData().length : -1;
                    System.out.println("      - Block [" + j + "] 特征 ID: " + block.getFeatureId() + " | 数据长度: " + dataLength);

                    if (dataLength == 13824) {
                        System.out.println("        (💡 提示: 13824 刚好等于 1 * 24 * 1 * 24 * 24)");
                    }
                }
                System.out.println("---------------------------------------------------------");
            }

        } catch (Exception e) {
            System.err.println("\n💥 运行过程中发生崩溃！原因：");
            e.printStackTrace();
        } finally {
            container.unload();
            System.out.println("\n🧹 容器资源已清理。");
        }
    }
}