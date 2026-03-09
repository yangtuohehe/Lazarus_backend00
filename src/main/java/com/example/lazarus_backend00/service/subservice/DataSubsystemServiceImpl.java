package com.example.lazarus_backend00.service.subservice;

import com.example.lazarus_backend00.component.clock.VirtualTimeTickEvent;
import com.example.lazarus_backend00.domain.axis.SpaceAxisX;
import com.example.lazarus_backend00.domain.axis.SpaceAxisY;
import com.example.lazarus_backend00.domain.axis.TimeAxis;
import com.example.lazarus_backend00.domain.data.DataState;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.domain.data.TSState;
import com.example.lazarus_backend00.domain.data.TSShellFactory;
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

    private static final int BROADCAST_INTERVAL_HOURS = 1;
    private Instant nextBroadcastTime;

    private static final String PATH_MEIJI_SOURCE = "D:\\CODE\\project\\Lazarus\\Data\\Meiji(1)-water\\Meiji\\data";
    private static final String PATH_REALTIME_DB = "D:\\CODE\\project\\Lazarus\\Data\\Realtime_DB";

    // ✅ 修改 1：将地址指向主系统的 SystemIntegrationController
    // 假设您的后端主服务运行在 8080 端口
    private static final String URL_MAIN_SYSTEM_NOTIFY = "http://localhost:8080/api/v1/orchestration/notify-batch";

    private final RestTemplate restTemplate;

    // 🔥 缓冲队列存放待发送的 TSState 位图状态协议
    private final Queue<TSState> notificationBuffer = new ConcurrentLinkedQueue<>();
    private final Map<String, Instant> ingestionCursors = new ConcurrentHashMap<>();
    private final List<FeatureProfile> profiles = new ArrayList<>();

    private static final DateTimeFormatter FMT_MEIJI = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withZone(java.time.ZoneOffset.UTC);

    public DataSubsystemServiceImpl(org.springframework.boot.web.client.RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();

        // ✅ 修改 2：将所有特征的 packetLossRate (倒数第二个参数) 和 replacementProb (倒数第一个参数) 设为 0.0
        // 这样只保留了 latency (延迟) 和 transmissionStep (打包步长) 的模拟，消除了所有的“随机出问题”概率
        profiles.add(new FeatureProfile(1, "salinity", PATH_MEIJI_SOURCE, ChronoUnit.HOURS, FMT_MEIJI, Duration.ofHours(24), 1, 0.0, 0.0));
        profiles.add(new FeatureProfile(2, "temp", PATH_MEIJI_SOURCE, ChronoUnit.HOURS, FMT_MEIJI, Duration.ofHours(24), 1, 0.0, 0.0));
        profiles.add(new FeatureProfile(3, "precip", PATH_MEIJI_SOURCE, ChronoUnit.HOURS, FMT_MEIJI, Duration.ofHours(24), 1, 0.0, 0.0));
        profiles.add(new FeatureProfile(4, "evap", PATH_MEIJI_SOURCE, ChronoUnit.HOURS, FMT_MEIJI, Duration.ofHours(24), 1, 0.0, 0.0));
        profiles.add(new FeatureProfile(5, "salinity05", PATH_MEIJI_SOURCE, ChronoUnit.HOURS, FMT_MEIJI, Duration.ofHours(24), 1, 0.0, 0.0));
        profiles.add(new FeatureProfile(7, "temp05", PATH_MEIJI_SOURCE, ChronoUnit.HOURS, FMT_MEIJI, Duration.ofHours(24), 1, 0.0, 0.0));
    }

    @Override
    @EventListener
    public void onVirtualTimeTick(VirtualTimeTickEvent event) {
        Instant currentSystemTime = event.getVirtualTime();

        // 1. 根据当前最新时钟，拉取对应的数据进入缓冲区
        executeIngestion(currentSystemTime);

        // 2. 没有任何拦截！只要缓冲区里有数据，立刻向主系统发起广播（Webhook）！
        flushNotificationBuffer();
    }

    @Override
    public void executeIngestion(Instant currentSystemTime) {
        for (FeatureProfile profile : profiles) {
            processFeature(profile, currentSystemTime);
        }
    }

    private void processFeature(FeatureProfile profile, Instant systemTime) {
        String name = profile.name;
        // 修改后：让游标从系统时钟的前 48 小时开始，确保它能越过 24 小时的延迟线
        ingestionCursors.putIfAbsent(name, systemTime.minus(Duration.ofHours(48)));
        Instant cursor = ingestionCursors.get(name);

        Instant visibleHorizon = systemTime.minus(profile.latency);
        long pendingSteps = profile.resolution.between(cursor, visibleHorizon);

        if (pendingSteps < profile.transmissionStep) return;

        long batches = pendingSteps / profile.transmissionStep;
        long stepsToProcess = batches * profile.transmissionStep;

        for (int i = 0; i < stepsToProcess; i++) {
            Instant nextTarget = cursor.plus(1, profile.resolution);

            // 这里的 Math.random() < 0.0 永远为 false，不会再发生丢包
            if (Math.random() < profile.packetLossRate) {
                cursor = nextTarget;
                continue;
            }

            int status = determineStatusAndCopy(profile, nextTarget);
            if (status > 0) {
                notificationBuffer.offer(createStatePacket(profile, nextTarget, status));
            }
            cursor = nextTarget;
        }
        ingestionCursors.put(name, cursor);
    }

    private int determineStatusAndCopy(FeatureProfile profile, Instant time) {
        Path dbDir = Paths.get(PATH_REALTIME_DB, profile.name);
        String timeStr = profile.formatter.format(time);
        String destFileName = timeStr + ".tif";
        String simFileName = timeStr + "-ls.tif";
        Path simPath = dbDir.resolve(simFileName);
        int status;

        if (Files.exists(simPath)) {
            // 这里 replacementProb 也是 0.0，所以遇到 -ls 仿真文件时，status 永远置 0，不会触发重复发送
            status = (Math.random() < profile.replacementProb) ? 2 : 0;
        } else {
            status = 1;
        }

        if (status > 0 && copyFile(profile, time, destFileName)) {
            return status;
        }
        return 0;
    }

    public void flushNotificationBuffer() {
        if (notificationBuffer.isEmpty()) return;
        List<TSState> batchToSend = new ArrayList<>(notificationBuffer);
        notificationBuffer.clear();
        try {
            restTemplate.postForObject(URL_MAIN_SYSTEM_NOTIFY, batchToSend, String.class);
            log.info("📢 [DataSubsystem] 成功向主系统广播了 {} 个 TIF 状态", batchToSend.size());
        } catch (Exception e) {
            log.error("❌ Broadcast failed: {}", e.getMessage());
        }
    }

    private boolean copyFile(FeatureProfile profile, Instant time, String destFileName) {
        String timeStr = profile.formatter.format(time);
        Path srcPath = Paths.get(profile.rootPath, profile.name + "_" + timeStr + ".tif");
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

    // 🔥 核心：生成整张 TIF 对应的 TSState (包含全局包络线与全满位图)
    private TSState createStatePacket(FeatureProfile profile, Instant time, int status) {

        // =====================================================================
        // 真实地理参数填充
        // =====================================================================

        // 1. 时间轴参数 (假设模型步长是 1小时 = 3600秒)
        double timeRes = 1.0;
        TimeAxis tAxis = new TimeAxis(timeRes, "Hours", timeRes, "Hours");
        tAxis.setType("TIME"); // 🎯 必须显式声明类型
        tAxis.setCount(1);     // 🎯 单帧数据

        // 2. X轴 (经度) 参数
        double originX = 115.425208;
        double resX = 0.009583;
        int countX = 24;
        double spanX = resX * countX;
        SpaceAxisX xAxis = new SpaceAxisX(spanX, "Degrees", resX, "Degrees");
        xAxis.setType("SPACE_X"); // 🎯 必须显式声明
        xAxis.setCount(countX);   // 🎯 补上像素数量！

        // 3. Y轴 (纬度) 参数
        double originY = 10.014792;
        double resY = 0.009583;
        int countY = 24;
        double spanY = resY * countY;
        SpaceAxisY yAxis = new SpaceAxisY(spanY, "Degrees", resY, "Degrees");
        yAxis.setType("SPACE_Y"); // 🎯 必须显式声明
        yAxis.setCount(countY);   // 🎯 补上像素数量！

        // =====================================================================

        // 构建带有真实硬编码参数的 TSShell
        TSShell shell = new TSShell.Builder(profile.featureId)
                .time(time, tAxis)
                .x(originX, xAxis)
                .y(originY, yAxis)
                .build();

        // 🎯 此时调用工厂，底层 TSState 的构造函数会计算出 24 * 24 = 576 的正确像素量，并将 576 位全部涂满！
        DataState dataState = (status == 2) ? DataState.REPLACED : DataState.READY;
        return TSShellFactory.createTSStateFromShell(shell, dataState);
    }
    @Override
    public void generateSimulationTif(String featureName, Instant time, TSDataBlock dataBlock) {
    }

    private static class FeatureProfile {
        int featureId; String name; String rootPath; ChronoUnit resolution; DateTimeFormatter formatter;
        Duration latency; int transmissionStep; double packetLossRate; double replacementProb;
        public FeatureProfile(int id, String name, String root, ChronoUnit res, DateTimeFormatter fmt, Duration lat, int step, double loss, double repl) {
            this.featureId = id; this.name = name; this.rootPath = root; this.resolution = res; this.formatter = fmt;
            this.latency = lat; this.transmissionStep = step; this.packetLossRate = loss; this.replacementProb = repl;
        }
    }
}