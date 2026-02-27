package com.example.lazarus_backend00.service.subservice;

import com.example.lazarus_backend00.component.clock.VirtualTimeTickEvent;
import com.example.lazarus_backend00.domain.axis.TimeAxis;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.dto.subdto.DataUpdatePacket;
import com.example.lazarus_backend00.service.subservice.DataSubsystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * 数据子系统仿真服务 (Data Subsystem Simulation Service)
 *
 * <p>职责：模拟外部环境监测系统（如 ERA5 气象数据、Meiji 海洋数据）的运行机制，
 * 作为整个数字孪生系统的 <b>主动数据生产者 (Active Producer)</b>。
 *
 * <p>核心特性：
 * <ol>
 * <li><b>时钟驱动 (Event-Driven)</b>：监听虚拟时钟信号，按仿真时间步推进，从历史库搬运 TIF 文件到实时库。</li>
 * <li><b>智能状态判定 (Smart Logic)</b>：
 * <ul>
 * <li>自动判断数据为 <b>新增 (Status=1)</b> 还是 <b>修正替换 (Status=2)</b>。</li>
 * <li>内置随机故障模拟（网络丢包）与数据修正概率，模拟真实的非理想数据环境。</li>
 * </ul>
 * </li>
 * <li><b>批量通知 (Buffered Notification)</b>：
 * 维护一个全局发送缓冲区，将高频的单点更新打包，通过 HTTP POST 定时批量推送给模型主系统，
 * 有效降低系统间的网络交互开销。
 * </li>
 * </ol>
 */

@Service
public class DataSubsystemServiceImpl implements DataSubsystemService {

    private static final Logger log = LoggerFactory.getLogger(DataSubsystemServiceImpl.class);

    // =========================================================================
    // ⚙️ [配置区域] 所有可调节参数均在此处
    // =========================================================================

    // 1. 路径配置
    private static final String PATH_ERA5_SOURCE = "D:\\CODE\\project\\Lazarus\\Data\\era5_processed_tifs";
    private static final String PATH_MEIJI_SOURCE = "D:\\CODE\\project\\Lazarus\\Data\\Meiji(1)-water\\Meiji\\data";
    private static final String PATH_REALTIME_DB = "D:\\CODE\\project\\Lazarus\\Data\\Realtime_DB";

    // 2. 主系统通信配置
    private static final String URL_MAIN_SYSTEM_NOTIFY = "http://localhost:8080/api/v1/system/integration/notify-batch";
    private static final long NOTIFY_INTERVAL_MS = 24 * 60 * 60 * 1000L;
    private static final int MAX_BATCH_SIZE = 200;      // 单次 HTTP 请求最大包数量

    // 3. 仿真策略配置
    private static final double PROB_REPLACE_DATA = 0.03; // 替换旧实测数据的概率 (3%)
    private static final double PROB_NETWORK_FAIL = 0.05; // 模拟网络丢包/传感器故障概率 (5%)

    // 4. 特征源定义 (ERA5 - Daily)
    // 格式: {FeatureName} (ID自动递增)
    private static final String[] FEATURES_ERA5 = {
            "temperature", "precipitation", "sea_surface_temperature",
            "solar_radiation", "wind_u", "wind_v"
    };

    // 5. 特征源定义 (Meiji - Hourly)
    private static final String[] FEATURES_MEIJI = {
            "evap", "precip", "salinity", "salinity05",
            "temp", "temp05", "tx", "ty",
            "u", "u05", "v", "v05", "zeta"
    };

    // =========================================================================
    // 🔧 [服务逻辑] 以下代码通常无需修改
    // =========================================================================

    private final RestTemplate restTemplate = new RestTemplate();
    private final Queue<DataUpdatePacket> notificationBuffer = new ConcurrentLinkedQueue<>();
    private final Map<String, Instant> ingestionCursors = new ConcurrentHashMap<>();
    private final List<FeatureProfile> profiles = new ArrayList<>();
    private final Random random = new Random();

    // 格式化器
    private static final DateTimeFormatter FMT_ERA5 = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FMT_MEIJI = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withZone(ZoneId.systemDefault());

    private enum SourceType { ERA5, MEIJI }

    /**
     * 初始化：加载配置并构建 Profile
     */
    public DataSubsystemServiceImpl() {
        int idCounter = 101; // ERA5 ID 起始
        for (String name : FEATURES_ERA5) {
            profiles.add(new FeatureProfile(
                    idCounter++, name, Duration.ofDays(random.nextInt(2)), 0.0,
                    SourceType.ERA5, PATH_ERA5_SOURCE, ChronoUnit.DAYS, FMT_ERA5, 1
            ));
        }

        idCounter = 201; // Meiji ID 起始
        for (String name : FEATURES_MEIJI) {
            profiles.add(new FeatureProfile(
                    idCounter++, name, Duration.ofHours(random.nextInt(5)), PROB_NETWORK_FAIL,
                    SourceType.MEIJI, PATH_MEIJI_SOURCE, ChronoUnit.HOURS, FMT_MEIJI, 3 + random.nextInt(4)
            ));
        }
        log.info(">>> [DataSubsystem] 初始化完成，加载 {} 个特征源。", profiles.size());
    }

    // ================= [入口] 时钟驱动 =================

    @Override
    @EventListener
    public void onVirtualTimeTick(VirtualTimeTickEvent event) {
        executeIngestion(event.getVirtualTime());
    }

    @Override
    public void executeIngestion(Instant currentSystemTime) {
        for (FeatureProfile profile : profiles) {
            processFeature(profile, currentSystemTime);
        }
    }

    // ================= [核心] 数据处理与状态机 =================

    private void processFeature(FeatureProfile profile, Instant systemTime) {
        String name = profile.name;
        ingestionCursors.putIfAbsent(name, getInitialStartTime());
        Instant cursor = ingestionCursors.get(name);

        Instant visibleHorizon = systemTime.minus(profile.latency);
        long pendingSteps = profile.resolution.between(cursor, visibleHorizon);

        if (pendingSteps < profile.batchSize) return;

        long batchesToProcess = pendingSteps / profile.batchSize;

        for (int b = 0; b < batchesToProcess; b++) {
            for (int i = 0; i < profile.batchSize; i++) {
                Instant nextTarget = cursor.plus(1, profile.resolution);

                // 模拟传感器/网络故障
                if (!shouldFail(profile.failureRate)) {

                    // 1. 🔥 在覆盖文件前，判定状态 (New vs Replace)
                    int status = determineStatusAndCopy(profile, nextTarget);

                    // 2. 如果状态有效 (>0) 且复制成功，加入发送缓冲区
                    if (status > 0) {
                        notificationBuffer.offer(createPacket(profile, nextTarget, status));
                    }
                }
                cursor = nextTarget;
            }
        }
        ingestionCursors.put(name, cursor);
    }

    /**
     * 判断状态并执行复制
     * @return 0:忽略/失败, 1:新增, 2:替换
     */
    private int determineStatusAndCopy(FeatureProfile profile, Instant time) {
        Path dbDir = Paths.get(PATH_REALTIME_DB, profile.name);

        // 构建目标文件名
        String timeStr = profile.formatter.format(time);
        String destFileName;
        if (profile.type == SourceType.ERA5) {
            destFileName = timeStr + ".tif";
        } else {
            destFileName = timeStr + ".tif"; // 统一去前缀
        }

        Path destPath = dbDir.resolve(destFileName);

        // --- 核心判定逻辑 ---
        int status;
        boolean alreadyExists = Files.exists(destPath);

        if (!alreadyExists) {
            status = 1; // 之前没文件 -> 新增
        } else {
            // 之前有文件 -> 概率替换
            if (random.nextDouble() < PROB_REPLACE_DATA) {
                status = 2; // 判定为替换 (修正数据)
            } else {
                status = 0; // 判定为忽略 (数据未变化或无需修正)
            }
        }

        // --- 执行物理复制 ---
        // 只有当 status > 0 时我们才真的关心这次复制是否成功
        // 但为了仿真完整性，只要不忽略，我们就执行 overwrite
        if (status > 0) {
            boolean success = copyFile(profile, time, destFileName);
            return success ? status : 0; // 如果IO失败，则取消通知
        }

        return 0;
    }

    // ================= [输出] 批量通知发送 =================

    @Scheduled(fixedRate = NOTIFY_INTERVAL_MS)
    public void flushNotificationBuffer() {
        if (notificationBuffer.isEmpty()) return;

        List<DataUpdatePacket> batchToSend = new ArrayList<>();
        while (!notificationBuffer.isEmpty()) {
            batchToSend.add(notificationBuffer.poll());
            if (batchToSend.size() >= MAX_BATCH_SIZE) break;
        }

        if (batchToSend.isEmpty()) return;

        try {
            log.info("📡 [Batch Notify] 向模型系统推送 {} 条数据更新...", batchToSend.size());
            restTemplate.postForObject(URL_MAIN_SYSTEM_NOTIFY, batchToSend, String.class);
        } catch (Exception e) {
            log.error("❌ [Notify Failed] 推送失败: {}", e.getMessage());
        }
    }

    // ================= [底层] 文件 IO =================

    private boolean copyFile(FeatureProfile profile, Instant time, String destFileName) {
        String timeStr = profile.formatter.format(time);
        Path srcPath;

        if (profile.type == SourceType.ERA5) {
            srcPath = Paths.get(profile.rootPath, profile.name, timeStr + ".tif");
        } else {
            srcPath = Paths.get(profile.rootPath, profile.name + "_" + timeStr + ".tif");
        }

        if (srcPath == null || !Files.exists(srcPath)) return false;

        try {
            Path dstDir = Paths.get(PATH_REALTIME_DB, profile.name);
            if (!Files.exists(dstDir)) Files.createDirectories(dstDir);

            Files.copy(srcPath, dstDir.resolve(destFileName), StandardCopyOption.REPLACE_EXISTING);
            copyTfw(srcPath, dstDir, destFileName);
            return true;
        } catch (IOException e) {
            log.error("IO Error: " + profile.name, e);
            return false;
        }
    }

    private void copyTfw(Path srcTif, Path dstDir, String dstTifName) {
        try {
            String srcStr = srcTif.toString();
            // 简单推断 .tfw
            Path srcTfw = Paths.get(srcStr.substring(0, srcStr.length() - 4) + ".tfw");
            if (Files.exists(srcTfw)) {
                String dstTfwName = dstTifName.substring(0, dstTifName.length() - 4) + ".tfw";
                Files.copy(srcTfw, dstDir.resolve(dstTfwName), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {}
    }

    // ================= [辅助] 工具方法 =================

    private DataUpdatePacket createPacket(FeatureProfile profile, Instant time, int status) {
        double res = (profile.resolution == ChronoUnit.HOURS) ? 3600.0 : 86400.0;
        TimeAxis tAxis = new TimeAxis(res, "Seconds", res, "Seconds");
        TSShell shell = new TSShell.Builder(profile.featureId).time(time, tAxis).build();
        return new DataUpdatePacket(shell, status);
    }

    private boolean shouldFail(double rate) { return rate > 0.0 && random.nextDouble() < rate; }
    private Instant getInitialStartTime() { return Instant.parse("2022-01-01T00:00:00Z"); }

    // ================= [内部类] Profile =================
    private static class FeatureProfile {
        int featureId;
        String name;
        Duration latency;
        double failureRate;
        SourceType type;
        String rootPath;
        ChronoUnit resolution;
        DateTimeFormatter formatter;
        int batchSize;

        public FeatureProfile(int featureId, String name, Duration latency, double failureRate, SourceType type, String rootPath, ChronoUnit resolution, DateTimeFormatter formatter, int batchSize) {
            this.featureId = featureId;
            this.name = name;
            this.latency = latency;
            this.failureRate = failureRate;
            this.type = type;
            this.rootPath = rootPath;
            this.resolution = resolution;
            this.formatter = formatter;
            this.batchSize = batchSize;
        }
    }
}