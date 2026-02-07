package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.component.clock.VirtualTimeTickEvent;
import com.example.lazarus_backend00.service.GlobalClockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private Duration stepSize;              // 步长（每次跳动前进多久，例如 1小时）
    private long playbackSpeedMs;           // 播放速度（现实中每隔多少毫秒执行一次跳动）

    // --- 控制标志 ---
    private final AtomicBoolean isRunning = new AtomicBoolean(false); // 是否正在自动演演

    public GlobalClockServiceImpl(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        // 默认初始化：当前时间，步长1小时，播放间隔1秒
        this.currentVirtualTime = Instant.now();
        this.stepSize = Duration.ofHours(1);
        this.playbackSpeedMs = 1000L;
    }

    /**
     * 【核心动作】执行一次时间步进
     * 无论是手动点击还是自动播放，最终都调用这里
     */
    public synchronized void tick() {
        // 1. 推进时间
        this.currentVirtualTime = this.currentVirtualTime.plus(stepSize);

        // 2. 广播事件 (由于模型计算假设为瞬时，这里使用同步广播即可)
        // Spring默认的publishEvent是同步的，这意味着所有监听器执行完，这行代码才算结束
        log.info("⏳ [Clock] Tick! 虚拟时间推进至: {}", currentVirtualTime);
        eventPublisher.publishEvent(new VirtualTimeTickEvent(this, currentVirtualTime));
    }

    /**
     * 定时任务：处理自动播放逻辑
     * 如果 isRunning 为 true，则按照 playbackSpeedMs 的频率自动 tick
     */
    @Scheduled(fixedDelay = 100) // 基础轮询频率，具体由内部逻辑控制
    public void autoPlayLoop() {
        if (!isRunning.get()) {
            return;
        }

        // 这里为了简单演示，直接调用 tick。
        // 在高精度要求下，你可能需要记录上一次 tick 的系统时间戳来控制精确的 playbackSpeedMs
        // 但对于演示系统，利用 Thread.sleep 或简单的频率控制即可
        try {
            tick();
            Thread.sleep(playbackSpeedMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // --- 控制 API ---

    /**
     * 1. 初始化/重置设置
     */
    public void reset(Instant startTime, Duration stepSize) {
        this.isRunning.set(false); // 重置时先暂停
        this.currentVirtualTime = startTime;
        this.stepSize = stepSize;
        log.info("🔄 [Clock] 重置完成. 起点: {}, 步长: {}", startTime, stepSize);
        // 重置后通常需要广播一次初始状态
        eventPublisher.publishEvent(new VirtualTimeTickEvent(this, currentVirtualTime));
    }

    /**
     * 2. 开始/继续自动演练
     */
    public void play() {
        if (isRunning.compareAndSet(false, true)) {
            log.info("▶️ [Clock] 开始自动演练");
        }
    }

    /**
     * 3. 暂停
     */
    public void pause() {
        if (isRunning.compareAndSet(true, false)) {
            log.info("⏸️ [Clock] 暂停演练");
        }
    }

    /**
     * 4. 手动单步执行 (Debug用)
     */
    public void stepOnce() {
        pause(); // 手动步进时，先强制暂停自动播放
        tick();
    }

    /**
     * 5. 修改播放速度 (现实时间间隔)
     * @param speedMs 现实中每隔多少毫秒跳一次
     */
    public void setPlaybackSpeed(long speedMs) {
        this.playbackSpeedMs = speedMs;
    }

    // --- Getters for UI ---
    public Instant getCurrentVirtualTime() { return currentVirtualTime; }
    public boolean isRunning() { return isRunning.get(); }
    public Duration getStepSize() { return stepSize; }
}