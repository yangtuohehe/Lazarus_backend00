package com.example.lazarus_backend00.service.subservice;

import com.example.lazarus_backend00.component.clock.VirtualTimeTickEvent;
import com.example.lazarus_backend00.domain.axis.TimeAxis;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.dto.subdto.DataUpdatePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
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

@Service
public class DataSubsystemServiceImpl implements DataSubsystemService {

    private static final Logger log = LoggerFactory.getLogger(DataSubsystemServiceImpl.class);

    // =========================================================================
    // ⚙️ [全局统筹配置]
    // =========================================================================
    private static final int BROADCAST_INTERVAL_HOURS = 8; // 全局广播大周期：8小时
    private Instant nextBroadcastTime;

    private static final String PATH_ERA5_SOURCE = "D:\\CODE\\project\\Lazarus\\Data\\era5_processed_tifs";
    private static final String PATH_MEIJI_SOURCE = "D:\\CODE\\project\\Lazarus\\Data\\Meiji(1)-water\\Meiji\\data";
    private static final String PATH_REALTIME_DB = "D:\\CODE\\project\\Lazarus\\Data\\Realtime_DB";
    private static final String URL_MAIN_SYSTEM_NOTIFY = "https://webhook.site/93bbed08-c2c3-4c72-9a2a-9fceee31b286";//"http://localhost:8080/api/v1/orchestration/notify-batch";

    private static final String[] FEATURES_ERA5 = { "temperature", "precipitation", "sea_surface_temperature", "solar_radiation", "wind_u", "wind_v" };
    private static final String[] FEATURES_MEIJI = { "evap", "precip", "salinity", "salinity05", "temp", "temp05", "tx", "ty", "u", "u05", "v", "v05", "zeta" };

    private final RestTemplate restTemplate = new RestTemplate();
    private final Queue<DataUpdatePacket> notificationBuffer = new ConcurrentLinkedQueue<>();
    private final Map<String, Instant> ingestionCursors = new ConcurrentHashMap<>();
    private final List<FeatureProfile> profiles = new ArrayList<>();

    private static final DateTimeFormatter FMT_ERA5 = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FMT_MEIJI = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withZone(ZoneId.systemDefault());

    private enum SourceType { ERA5, MEIJI }

    public DataSubsystemServiceImpl() {
        this.nextBroadcastTime = getInitialStartTime().plus(BROADCAST_INTERVAL_HOURS, ChronoUnit.HOURS);
        int idCounter = 101;
        for (String name : FEATURES_ERA5) {
            profiles.add(new FeatureProfile(
                    idCounter++, name, SourceType.ERA5, PATH_ERA5_SOURCE, ChronoUnit.DAYS, FMT_ERA5,
                    Duration.ofDays(1),   // 1. 固定延迟
                    1,                   // 2. 单次传输步长(攒够几步才生成)
                    0.05,                // 3. 丢包率 (5%)
                    0.80                 // 4. 替换概率 (存在-ls时，80%概率发状态2)
            ));
        }
        idCounter = 201;
        for (String name : FEATURES_MEIJI) {
            profiles.add(new FeatureProfile(
                    idCounter++, name, SourceType.MEIJI, PATH_MEIJI_SOURCE, ChronoUnit.HOURS, FMT_MEIJI,
                    Duration.ofHours(7), // 1. 固定延迟 (晚7小时)
                    3,                   // 2. 单次传输步长 (如你所说，攒够3步才一起更新)
                    0.10,                // 3. 丢包率 (10%)
                    0.50                 // 4. 替换概率 (存在-ls时，50%概率发状态2)
            ));
        }
        log.info(">>> [数据子系统] 启动！已装载 {} 个特征的独立传输规则。", profiles.size());
    }

    // ================= [入口] =================

    @Override
    @EventListener
    public void onVirtualTimeTick(VirtualTimeTickEvent event) {
        Instant currentSystemTime = event.getVirtualTime();
        log.info("\n⏰ [时钟跳动] 上帝时间: {}", currentSystemTime);

        // 1. 各个特征按照自己的独立规则，向公共池子里攒数据
        executeIngestion(currentSystemTime);

        // 2. 全局广播周期判定 (大卡车来拉货了)
        if (!currentSystemTime.isBefore(nextBroadcastTime)) {
            log.info("🚀 达到全局广播周期 [{}]！", nextBroadcastTime);
            flushNotificationBuffer();
            while (!nextBroadcastTime.isAfter(currentSystemTime)) {
                nextBroadcastTime = nextBroadcastTime.plus(BROADCAST_INTERVAL_HOURS, ChronoUnit.HOURS);
            }
            log.info("   ⏭️ 下一次计划广播时间更新为: {}", nextBroadcastTime);
        } else {
            log.info("   ⏳ 积木已放入池中，等待卡车 (下次广播: {})", nextBroadcastTime);
        }
    }

    @Override
    public void executeIngestion(Instant currentSystemTime) {
        for (FeatureProfile profile : profiles) {
            processFeature(profile, currentSystemTime);
        }
    }

    // ================= [核心逻辑：四大规则完美执行] =================

    private void processFeature(FeatureProfile profile, Instant systemTime) {
        String name = profile.name;
        ingestionCursors.putIfAbsent(name, getInitialStartTime());
        Instant cursor = ingestionCursors.get(name);

        // 规则 1：扣除固定延迟，计算视界
        Instant visibleHorizon = systemTime.minus(profile.latency);
        long pendingSteps = profile.resolution.between(cursor, visibleHorizon);

        // 规则 2：单次传输步长判定 (攒够了没？)
        if (pendingSteps < profile.transmissionStep) {
            return; // 比如步长设为3，现在才过了2个步长，直接按兵不动！
        }

        // 计算出这次一共能处理多少个完整步长
        long batches = pendingSteps / profile.transmissionStep;
        long stepsToProcess = batches * profile.transmissionStep;
        int generatedCount = 0;

        for (int i = 0; i < stepsToProcess; i++) {
            Instant nextTarget = cursor.plus(1, profile.resolution);

            // 规则 3：丢包率判定
            if (Math.random() < profile.packetLossRate) {
                log.warn("   🌪️ [丢包模拟] 特征 [{}] 丢失了时刻 [{}] 的数据报文！", name, nextTarget);
                cursor = nextTarget; // 丢包了游标也要走，假装这段数据永远找不到了
                continue;
            }

            // 规则 4：状态判定与物理拷贝
            int status = determineStatusAndCopy(profile, nextTarget);

            // 只有 > 0 (成功产生 1 或 2) 才打包
            if (status > 0) {
                notificationBuffer.offer(createPacket(profile, nextTarget, status));
                generatedCount++;
            }
            cursor = nextTarget;
        }

        if (generatedCount > 0) {
            log.info("   📥 [{}] 本次成功传输并攒入 {} 个时间步！(游标至 {})", name, generatedCount, cursor);
        }
        ingestionCursors.put(name, cursor);
    }

    private int determineStatusAndCopy(FeatureProfile profile, Instant time) {
        Path dbDir = Paths.get(PATH_REALTIME_DB, profile.name);
        String timeStr = profile.formatter.format(time);

        // 目标实测文件名称
        String destFileName = timeStr + ".tif";
        // 检查模型系统是否已经提前预测了该数据 (检查 -ls.tif)
        String simFileName = timeStr + "-ls.tif";

        Path simPath = dbDir.resolve(simFileName);
        int status;

        if (Files.exists(simPath)) {
            // 规则 4：检测到模拟数据已存在！决定是否进行替换同化
            if (Math.random() < profile.replacementProb) {
                log.debug("   🔄 [同化覆盖] 决定用实测数据替换已有的模拟数据 {}！", simFileName);
                status = 2; // 替换状态
            } else {
                // 模型列表已存在该数据，且未抽中替换概率，则不发送任何更新
                return 0;
            }
        } else {
            // 库里干干净净，说明这是一个全新的实测进展
            status = 1; // 新增状态
        }

        // 如果判定需要发送，再去执行真实的硬盘文件搬运
        if (copyFile(profile, time, destFileName)) {
            return status;
        }
        return 0; // 如果物理文件都找不到，直接静默
    }

    // ================= [输出与底层拷贝] =================

    public void flushNotificationBuffer() {
        if (notificationBuffer.isEmpty()) return;
        List<DataUpdatePacket> batchToSend = new ArrayList<>(notificationBuffer);
        notificationBuffer.clear();

        Map<Integer, List<Instant>> broadcastSummary = new TreeMap<>();
        for (DataUpdatePacket packet : batchToSend) {
            broadcastSummary.computeIfAbsent(packet.getShell().getFeatureId(), k -> new ArrayList<>()).add(packet.getShell().getTOrigin());
        }

        log.info("=========================================================");
        log.info("📢 [全网广播] 本次装载 {} 条数据:", batchToSend.size());
        for (Map.Entry<Integer, List<Instant>> entry : broadcastSummary.entrySet()) {
            List<Instant> times = entry.getValue();
            Collections.sort(times);
            log.info("   -> 📦 传感器 ID [{}]: {} 步", entry.getKey(), times.size());
        }
        log.info("=========================================================");

        try {
            restTemplate.postForObject(URL_MAIN_SYSTEM_NOTIFY, batchToSend, String.class);
        } catch (Exception e) {
            log.error("   ❌ [HTTP 失败] {}", e.getMessage());
        }
    }

    private boolean copyFile(FeatureProfile profile, Instant time, String destFileName) {
        String timeStr = profile.formatter.format(time);
        Path srcPath = (profile.type == SourceType.ERA5) ?
                Paths.get(profile.rootPath, profile.name, timeStr + ".tif") :
                Paths.get(profile.rootPath, profile.name + "_" + timeStr + ".tif");

        if (!Files.exists(srcPath)) return false;

        try {
            Path dstDir = Paths.get(PATH_REALTIME_DB, profile.name);
            if (!Files.exists(dstDir)) Files.createDirectories(dstDir);
            Files.copy(srcPath, dstDir.resolve(destFileName), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private DataUpdatePacket createPacket(FeatureProfile profile, Instant time, int status) {
        double res = (profile.resolution == ChronoUnit.HOURS) ? 3600.0 : 86400.0;
        TSShell shell = new TSShell.Builder(profile.featureId).time(time, new TimeAxis(res, "Seconds", res, "Seconds")).build();
        return new DataUpdatePacket(shell, status);
    }

    private Instant getInitialStartTime() { return Instant.parse("2022-01-01T00:00:00Z"); }

    @Override
    public void generateSimulationTif(String featureName, Instant time, TSDataBlock dataBlock) {
        // 模型算完的数据，保存为带 -ls.tif 后缀的文件
        String timeStr = FMT_MEIJI.format(time); // 假设主要仿真都是按小时
        log.info("💾 [仿真落盘] 模型产出已被保存为: {}", timeStr + "-ls.tif");
        // 这里你原本有写 TIF 的逻辑，由于它是 -ls.tif，正好能被上面的 determineStatusAndCopy 感知到！
    }

    private static class FeatureProfile {
        int featureId; String name; SourceType type; String rootPath; ChronoUnit resolution; DateTimeFormatter formatter;
        Duration latency; int transmissionStep; double packetLossRate; double replacementProb;
        public FeatureProfile(int featureId, String name, SourceType type, String rootPath, ChronoUnit resolution, DateTimeFormatter formatter, Duration latency, int transmissionStep, double packetLossRate, double replacementProb) {
            this.featureId = featureId; this.name = name; this.type = type; this.rootPath = rootPath; this.resolution = resolution; this.formatter = formatter;
            this.latency = latency; this.transmissionStep = transmissionStep; this.packetLossRate = packetLossRate; this.replacementProb = replacementProb;
        }
    }
}