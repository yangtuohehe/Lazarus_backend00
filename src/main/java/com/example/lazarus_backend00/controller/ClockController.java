package com.example.lazarus_backend00.controller;

import com.example.lazarus_backend00.dto.ClockStatusDTO;
import com.example.lazarus_backend00.service.GlobalClockService;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;

/**
 * 仿真时钟控制面板
 * 前端 UI 通过此 API 控制“上帝时间”
 */
@RestController
@RequestMapping("/api/clock")
@CrossOrigin // 允许前端开发时跨域调试
public class ClockController {

    // 注入接口，而不是具体的 GlobalClockServiceImpl
    private final GlobalClockService clockService;

    public ClockController(GlobalClockService clockService) {
        this.clockService = clockService;
    }

    /**
     * 🟢 获取当前状态 (轮询用)
     * GET /api/clock/status
     */
    @GetMapping("/status")
    public ClockStatusDTO getStatus() {
        return new ClockStatusDTO(
                clockService.getCurrentVirtualTime(),
                clockService.isRunning(),
                clockService.getStepSize()
        );
    }

    /**
     * 🔄 初始化/重置时钟
     * POST /api/clock/reset?startTime=2026-01-01T00:00:00Z&stepHours=1
     */
    @PostMapping("/reset")
    public String reset(
            @RequestParam String startTime,
            @RequestParam(defaultValue = "1") int stepHours) {

        Instant start = Instant.parse(startTime); // 解析 ISO-8601 格式字符串
        Duration step = Duration.ofHours(stepHours);

        clockService.reset(start, step);
        return "✅ Clock reset to " + startTime + " with step " + stepHours + "h";
    }

    /**
     * ▶️ 开始自动演练
     * POST /api/clock/play
     */
    @PostMapping("/play")
    public String play() {
        clockService.play();
        return "▶️ Simulation started";
    }

    /**
     * ⏸️ 暂停演练
     * POST /api/clock/pause
     */
    @PostMapping("/pause")
    public String pause() {
        clockService.pause();
        return "⏸️ Simulation paused";
    }

    /**
     * ⏭️ 手动单步执行 (下一帧)
     * 无论当前是否暂停，强制推进一步。返回推进后的最新状态。
     * POST /api/clock/step
     */
    @PostMapping("/step")
    public ClockStatusDTO step() {
        clockService.stepOnce();
        return getStatus(); // 直接返回最新状态方便前端更新 UI
    }

    /**
     * ⏩ 设置播放倍速 (仅影响自动播放)
     * POST /api/clock/speed?intervalMs=500
     * 设置现实世界每多少毫秒跳动一次 (值越小越快)
     */
    @PostMapping("/speed")
    public String setSpeed(@RequestParam long intervalMs) {
        clockService.setPlaybackSpeed(intervalMs);
        return "⏩ Playback speed set to tick every " + intervalMs + "ms";
    }
}