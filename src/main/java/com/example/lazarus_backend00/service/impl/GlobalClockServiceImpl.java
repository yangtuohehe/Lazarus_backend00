package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.component.clock.VirtualTimeTickEvent;
import com.example.lazarus_backend00.service.GlobalClockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 仿真时钟核心服务
 * 职责：维护当前虚拟时间，控制时间流逝速度，广播时间Tick事件。
 */
@Service
public class GlobalClockServiceImpl implements GlobalClockService {

    private static final Logger log = LoggerFactory.getLogger(GlobalClockServiceImpl.class);

    private final ApplicationEventPublisher eventPublisher;

    // --- 核心状态 ---
    private Instant currentVirtualTime;     // 当前虚拟时间
    private Duration stepSize;              // 步长
    private long playbackSpeedMs;           // 播放速度

    // --- 控制标志 ---
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // 🔥 核心修改：通过 @Value 读取 application.properties 中的配置
    public GlobalClockServiceImpl(
            ApplicationEventPublisher eventPublisher,
            @Value("${simulation.start-time:2022-01-01T00:00:00Z}") String startTimeStr) {

        this.eventPublisher = eventPublisher;

        // 解析配置文件中读取到的字符串为 Instant 时间
        this.currentVirtualTime = Instant.parse(startTimeStr);
        this.stepSize = Duration.ofHours(1);
        this.playbackSpeedMs = 1000L;

        log.info("⏰ [Clock] 系统时钟初始化完成。起始时间读取为: {}", this.currentVirtualTime);
    }

    public synchronized void tick() {
        this.currentVirtualTime = this.currentVirtualTime.plus(stepSize);
        log.info("⏳ [Clock] Tick! 虚拟时间推进至: {}", currentVirtualTime);
        eventPublisher.publishEvent(new VirtualTimeTickEvent(this, currentVirtualTime));
    }

    @Scheduled(fixedDelay = 100)
    public void autoPlayLoop() {
        if (!isRunning.get()) return;
        try {
            tick();
            Thread.sleep(playbackSpeedMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void reset(Instant startTime, Duration stepSize) {
        this.isRunning.set(false);
        this.currentVirtualTime = startTime;
        this.stepSize = stepSize;
        log.info("🔄 [Clock] 重置完成. 起点: {}, 步长: {}", startTime, stepSize);
        eventPublisher.publishEvent(new VirtualTimeTickEvent(this, currentVirtualTime));
    }

    public void play() {
        if (isRunning.compareAndSet(false, true)) {
            log.info("▶️ [Clock] 开始自动演练");
        }
    }

    public void pause() {
        if (isRunning.compareAndSet(true, false)) {
            log.info("⏸️ [Clock] 暂停演练");
        }
    }

    public void stepOnce() {
        pause();
        tick();
    }

    public void setPlaybackSpeed(long speedMs) {
        this.playbackSpeedMs = speedMs;
    }

    public Instant getCurrentVirtualTime() { return currentVirtualTime; }
    public boolean isRunning() { return isRunning.get(); }
    public Duration getStepSize() { return stepSize; }
}