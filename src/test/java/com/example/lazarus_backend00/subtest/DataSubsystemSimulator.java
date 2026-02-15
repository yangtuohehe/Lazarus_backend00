package com.example.lazarus_backend00.subtest;

import com.example.lazarus_backend00.controller.subcontroller.DataSubsystemController;
import com.example.lazarus_backend00.domain.axis.SpaceAxisX;
import com.example.lazarus_backend00.domain.axis.SpaceAxisY;
import com.example.lazarus_backend00.domain.axis.TimeAxis;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.dto.subdto.DataCheckResult;
import com.example.lazarus_backend00.service.subservice.DataStorageServiceImpl;
import com.example.lazarus_backend00.service.subservice.DataSubsystemServiceImpl;
import com.example.lazarus_backend00.service.subservice.FeatureMetadataManager;
import org.springframework.http.ResponseEntity;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 全功能仿真测试器
 */
public class DataSubsystemSimulator {

    private static final DateTimeFormatter FMT_LOG = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DecimalFormat FMT_VAL = new DecimalFormat("#.##");

    public static void main(String[] args) {
        // ---------------------------------------------------------
        // 1. 组装环境 (模拟 Spring 依赖注入)
        // ---------------------------------------------------------
        FeatureMetadataManager metadataManager = new FeatureMetadataManager();

        // 生成者
        DataSubsystemServiceImpl subsystemService = new DataSubsystemServiceImpl();
        // 管理者
        DataStorageServiceImpl storageService = new DataStorageServiceImpl(metadataManager);

        // 控制器
        DataSubsystemController controller = new DataSubsystemController(subsystemService, storageService);

        // ---------------------------------------------------------
        // 2. 初始化时间
        // ---------------------------------------------------------
        // 建议从 2022-01-01 开始，配合 Meiji 原始数据
        Instant sysTime = Instant.parse("2022-01-01T12:00:00Z");
        Scanner scanner = new Scanner(System.in);

        System.out.println("====== Lazarus Data Subsystem Simulator ======");
        System.out.println("System Time: " + FMT_LOG.format(sysTime));
        System.out.println("----------------------------------------------");

        // ---------------------------------------------------------
        // 3. 交互循环
        // ---------------------------------------------------------
        boolean running = true;
        while (running) {
            System.out.println("\n[MENU]");
            System.out.println("  [N] Next Day    -> 步进时间并生成实测数据 (/sync)");
            System.out.println("  [C] Check       -> 检查数据状态 (/status/check)");
            System.out.println("  [F] Fetch       -> 读取数据内容 (/data/fetch)");
            System.out.println("  [S] Sim Ingest  -> 写入仿真结果 (/data/ingest-calc)");
            System.out.println("  [Q] Quit");
            System.out.print(">>> ");

            String input = scanner.next();

            if (input.matches("-?\\d+")) {
                // 输入数字直接步进天数
                int days = Integer.parseInt(input);
                sysTime = sysTime.plus(days, ChronoUnit.DAYS);
                System.out.println("🕒 Time Advanced -> " + FMT_LOG.format(sysTime));
                controller.syncData(sysTime); // 触发 DataSubsystemService
            } else {
                switch (input.toUpperCase()) {
                    case "N":
                        sysTime = sysTime.plus(1, ChronoUnit.DAYS);
                        System.out.println("🕒 Time Advanced -> " + FMT_LOG.format(sysTime));
                        controller.syncData(sysTime);
                        break;
                    case "C":
                        testStatusCheck(controller, scanner, sysTime);
                        break;
                    case "F":
                        testDataFetch(controller, scanner, sysTime);
                        break;
                    case "S":
                        testSimIngest(controller, scanner, sysTime);
                        break;
                    case "Q":
                        running = false;
                        break;
                    default:
                        System.out.println("Unknown command.");
                }
            }
        }
        scanner.close();
        System.out.println("Bye.");
    }

    // =============================================================
    // 测试模块
    // =============================================================

    /**
     * 测试状态检查 (Check)
     */
    private static void testStatusCheck(DataSubsystemController controller, Scanner scanner, Instant defaultTime) {
        System.out.println("\n--- Check Status ---");
        System.out.print("FeatureID (def:1): ");
        int fid = getInt(scanner, 1);

        // 检查以当前时间为起点的未来 6 小时
        TimeAxis axis = new TimeAxis(6 * 3600.0, "s", 3600.0, "s");
        TSShell shell = new TSShell.Builder(fid).time(defaultTime, axis).build();

        ResponseEntity<List<DataCheckResult>> res = controller.checkDataStatus(Collections.singletonList(shell));

        if (res.getBody() != null) {
            for (DataCheckResult r : res.getBody()) {
                String statusStr = switch (r.getStatus()) {
                    case 1 -> "🟢 Real";
                    case 2 -> "🔵 Sim ";
                    default -> "🔴 Miss";
                };
                System.out.printf("   %s | %s | %s%n",
                        statusStr, FMT_LOG.format(r.getTimestamp()), r.getFileName());
            }
        }
    }

    /**
     * 测试数据读取 (Fetch)
     */
    private static void testDataFetch(DataSubsystemController controller, Scanner scanner, Instant defaultTime) {
        System.out.println("\n--- Fetch Data ---");
        System.out.print("FeatureID (def:1): ");
        int fid = getInt(scanner, 1);

        // 读取 1 个时间步，10x10 网格
        TimeAxis tAxis = new TimeAxis(3600.0, "s", 3600.0, "s");
        SpaceAxisY yAxis = new SpaceAxisY(1.0, "deg", 0.1, "deg"); // 10 points
        SpaceAxisX xAxis = new SpaceAxisX(1.0, "deg", 0.1, "deg"); // 10 points

        TSShell shell = new TSShell.Builder(fid)
                .time(defaultTime, tAxis)
                .y(10.0, yAxis)
                .x(115.0, xAxis)
                .build();

        System.out.println("Fetching...");
        ResponseEntity<List<TSDataBlock>> res = controller.fetchData(Collections.singletonList(shell));

        if (res.getBody() != null && !res.getBody().isEmpty()) {
            TSDataBlock block = res.getBody().get(0);
            float[] data = block.getData();

            // 简单统计
            int valid = 0;
            float firstVal = Float.NaN;
            for (float v : data) {
                if (!Float.isNaN(v)) {
                    valid++;
                    if (Float.isNaN(firstVal)) firstVal = v;
                }
            }

            System.out.println("✅ Success!");
            System.out.println("   Feature: " + block.getFeatureId());
            System.out.println("   Total Points: " + data.length);
            System.out.println("   Valid Points: " + valid);
            System.out.println("   First Value : " + firstVal);
        } else {
            System.out.println("❌ Fetch returned empty or null.");
        }
    }

    /**
     * 测试仿真数据入库 (Sim Ingest)
     */
    private static void testSimIngest(DataSubsystemController controller, Scanner scanner, Instant defaultTime) {
        System.out.println("\n--- Sim Ingest (-ls) ---");
        System.out.print("FeatureID (def:1): ");
        int fid = getInt(scanner, 1);

        // 构造一个 10x10 的全 99.9 的数据块
        int size = 10 * 10;
        float[] fakeData = new float[size];
        Arrays.fill(fakeData, 99.9f);

        TimeAxis tAxis = new TimeAxis(3600.0, "s", 3600.0, "s");
        SpaceAxisY yAxis = new SpaceAxisY(1.0, "deg", 0.1, "deg");
        SpaceAxisX xAxis = new SpaceAxisX(1.0, "deg", 0.1, "deg");

        TSDataBlock block = new TSDataBlock.Builder()
                .featureId(fid)
                .time(defaultTime, tAxis)
                .y(10.0, yAxis)
                .x(115.0, xAxis)
                .data(fakeData)
                .build();

        System.out.println("Sending Sim Data...");
        ResponseEntity<String> res = controller.ingestCalculatedData(block);
        System.out.println("<<<Response: " + res.getBody());
    }

    // 辅助: 读取整数或默认值
    private static int getInt(Scanner sc, int def) {
        String s = sc.next();
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}