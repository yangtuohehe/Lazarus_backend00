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

    private static final int BROADCAST_INTERVAL_HOURS = 8;
    private Instant nextBroadcastTime;

    private static final String PATH_MEIJI_SOURCE = "D:\\CODE\\project\\Lazarus\\Data\\Meiji(1)-water\\Meiji\\data";
    private static final String PATH_REALTIME_DB = "D:\\CODE\\project\\Lazarus\\Data\\Realtime_DB";
    private static final String URL_MAIN_SYSTEM_NOTIFY = "https://webhook.site/93bbed08-c2c3-4c72-9a2a-9fceee31b286";

    private final RestTemplate restTemplate;

    // 🔥 缓冲队列存放待发送的 TSState 位图状态协议
    private final Queue<TSState> notificationBuffer = new ConcurrentLinkedQueue<>();
    private final Map<String, Instant> ingestionCursors = new ConcurrentHashMap<>();
    private final List<FeatureProfile> profiles = new ArrayList<>();

    private static final DateTimeFormatter FMT_MEIJI = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withZone(ZoneId.systemDefault());

    public DataSubsystemServiceImpl(org.springframework.boot.web.client.RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
        profiles.add(new FeatureProfile(1, "salinity", PATH_MEIJI_SOURCE, ChronoUnit.HOURS, FMT_MEIJI, Duration.ofHours(7), 3, 0.10, 0.50));
        profiles.add(new FeatureProfile(2, "temp", PATH_MEIJI_SOURCE, ChronoUnit.HOURS, FMT_MEIJI, Duration.ofHours(5), 3, 0.05, 0.80));
        profiles.add(new FeatureProfile(3, "precip", PATH_MEIJI_SOURCE, ChronoUnit.HOURS, FMT_MEIJI, Duration.ofHours(7), 6, 0.15, 0.40));
        profiles.add(new FeatureProfile(4, "evap", PATH_MEIJI_SOURCE, ChronoUnit.HOURS, FMT_MEIJI, Duration.ofHours(7), 3, 0.30, 0.50));
        profiles.add(new FeatureProfile(5, "salinity05", PATH_MEIJI_SOURCE, ChronoUnit.HOURS, FMT_MEIJI, Duration.ofHours(12), 3, 0.10, 0.50));
        profiles.add(new FeatureProfile(7, "temp05", PATH_MEIJI_SOURCE, ChronoUnit.HOURS, FMT_MEIJI, Duration.ofHours(7), 3, 0.10, 0.95));
    }

    @Override
    @EventListener
    public void onVirtualTimeTick(VirtualTimeTickEvent event) {
        Instant currentSystemTime = event.getVirtualTime();
        if (this.nextBroadcastTime == null) {
            this.nextBroadcastTime = currentSystemTime.plus(BROADCAST_INTERVAL_HOURS, ChronoUnit.HOURS);
        }
        executeIngestion(currentSystemTime);

        if (!currentSystemTime.isBefore(nextBroadcastTime)) {
            flushNotificationBuffer();
            while (!nextBroadcastTime.isAfter(currentSystemTime)) {
                nextBroadcastTime = nextBroadcastTime.plus(BROADCAST_INTERVAL_HOURS, ChronoUnit.HOURS);
            }
        }
    }

    @Override
    public void executeIngestion(Instant currentSystemTime) {
        for (FeatureProfile profile : profiles) {
            processFeature(profile, currentSystemTime);
        }
    }

    private void processFeature(FeatureProfile profile, Instant systemTime) {
        String name = profile.name;
        ingestionCursors.putIfAbsent(name, systemTime);
        Instant cursor = ingestionCursors.get(name);

        Instant visibleHorizon = systemTime.minus(profile.latency);
        long pendingSteps = profile.resolution.between(cursor, visibleHorizon);

        if (pendingSteps < profile.transmissionStep) return;

        long batches = pendingSteps / profile.transmissionStep;
        long stepsToProcess = batches * profile.transmissionStep;

        for (int i = 0; i < stepsToProcess; i++) {
            Instant nextTarget = cursor.plus(1, profile.resolution);
            if (Math.random() < profile.packetLossRate) {
                cursor = nextTarget;
                continue;
            }

            int status = determineStatusAndCopy(profile, nextTarget);
            if (status > 0) {
                // 🔥 将查到的新 TIF 生成 TSState 状态并入队
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
        double res = 3600.0;

        TSShell shell = new TSShell.Builder(profile.featureId)
                .time(time, new TimeAxis(res, "Seconds", res, "Seconds"))
                // 修复：X轴跨度 360度，分辨率 1度 (这样 count 才会算出 360)
                .x(-180.0, new SpaceAxisX(360.0, "Degrees", 1.0, "Degrees"))
                // 修复：Y轴跨度 180度，分辨率 1度 (这样 count 才会算出 180)
                .y(-90.0, new SpaceAxisY(180.0, "Degrees", 1.0, "Degrees"))
                .build();

        DataState dataState = (status == 2) ? DataState.REPLACED : DataState.READY;
        return TSShellFactory.createTSStateFromShell(shell, dataState);
    }

    @Override
    public void generateSimulationTif(String featureName, Instant time, TSDataBlock dataBlock) {
        // ... (原样保留你的创建空 .tif 逻辑)
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