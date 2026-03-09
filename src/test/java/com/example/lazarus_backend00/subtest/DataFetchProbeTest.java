package com.example.lazarus_backend00.subtest;

import com.example.lazarus_backend00.domain.axis.SpaceAxisX;
import com.example.lazarus_backend00.domain.axis.SpaceAxisY;
import com.example.lazarus_backend00.domain.axis.TimeAxis;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.service.DataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class DataFetchProbeTest {

    @Autowired
    private DataService dataService;

    @Test
    public void probeDataSubsystem() {
        System.out.println("\n🚀 === 开始探测底层数据获取端 (Data Subsystem) ===");

        // 1. 构造和 Task-1000 输入端一模一样的时空查询外壳 (查询24小时)
        int featureId = 1; // 测试 特征1
        Instant startTime = Instant.parse("2022-01-01T02:00:00Z"); // 你日志里爆毒的起始时间

        TimeAxis tAxis = new TimeAxis(1.0, "Hours", 1.0, "Hours");
        tAxis.setCount(24); // 连查 24 小时

        SpaceAxisX xAxis = new SpaceAxisX(0.009583 * 24, "Degrees", 0.009583, "Degrees");
        xAxis.setCount(24);
        SpaceAxisY yAxis = new SpaceAxisY(0.009583 * 24, "Degrees", 0.009583, "Degrees");
        yAxis.setCount(24);

        TSShell shell = new TSShell.Builder(featureId)
                .time(startTime, tAxis)
                .x(115.425208, xAxis) // 依据你之前的数据经纬度原点
                .y(10.014792, yAxis)
                .build();

        System.out.println("📦 构造探测外壳: [" + startTime + " to " + startTime.plusSeconds(24*3600) + "]");

        // 2. 直接向数据端要数据 (这会直接触发读取 .tif 文件)
        TSDataBlock resultBlock = dataService.fetchData(shell);

        if (resultBlock == null || resultBlock.getData() == null) {
            System.err.println("❌ 数据层返回了 null！");
            return;
        }

        float[] data = resultBlock.getData();
        int frameSize = 24 * 24; // 576 像素/帧
        int totalFrames = data.length / frameSize;

        System.out.println("✅ 成功拉取数据块，总长度: " + data.length);
        System.out.println("--------------------------------------------------");

        // 3. 逐帧扫描 NaN，抓出现场！
        int totalNan = 0;
        for (int t = 0; t < totalFrames; t++) {
            Instant currentFrameTime = startTime.plusSeconds(t * 3600L);
            int offset = t * frameSize;
            int frameNanCount = 0;

            for (int k = 0; k < frameSize; k++) {
                if (Float.isNaN(data[offset + k])) {
                    frameNanCount++;
                    totalNan++;
                }
            }

            if (frameNanCount > 0) {
                System.err.println("⚠️ 找不着文件！时刻 " + currentFrameTime + " | 被填充了 NaN 数量: " + frameNanCount);
            } else {
                System.out.println("🟢 文件存在！时刻 " + currentFrameTime + " | 数据完美 (无 NaN)");
            }
        }

        System.out.println("--------------------------------------------------");
        System.out.println("📊 探测总结: 总计查出 NaN 数量: " + totalNan);
        if (totalNan == 13248) {
            System.out.println("💡 完美吻合！你被数据层的“找不到文件就塞 NaN”逻辑坑了！");
            System.out.println("👉 解决方案：你需要检查在 2022-01-01 这天，为什么 Realtime_DB 里只有 1 个小时的文件！是冷启动数据不足？还是文件命名没对上？");
        }
    }
}