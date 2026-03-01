package com.example.lazarus_backend00.dto;

import java.time.Instant;

public class ClockStatusDTO {
    private Instant currentVirtualTime;
    private boolean isRunning;
    private long stepSizeBytes;
    private String stepSizeDesc;

    // 🔥 核心修复：添加无参构造函数，解决 JSON 反序列化失败问题
    public ClockStatusDTO() {
    }

    public ClockStatusDTO(Instant time, boolean running, java.time.Duration step) {
        this.currentVirtualTime = time;
        this.isRunning = running;
        // 增加安全判断，防止极端情况下的 null 传入
        this.stepSizeBytes = step != null ? step.getSeconds() : 0;
        this.stepSizeDesc = step != null ? step.toString() : "None";
    }

    public Instant getCurrentVirtualTime() {
        return currentVirtualTime;
    }

    public void setCurrentVirtualTime(Instant currentVirtualTime) {
        this.currentVirtualTime = currentVirtualTime;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        this.isRunning = running;
    }

    public long getStepSizeBytes() {
        return stepSizeBytes;
    }

    public void setStepSizeBytes(long stepSizeBytes) {
        this.stepSizeBytes = stepSizeBytes;
    }

    public String getStepSizeDesc() {
        return stepSizeDesc;
    }

    public void setStepSizeDesc(String stepSizeDesc) {
        this.stepSizeDesc = stepSizeDesc;
    }
}