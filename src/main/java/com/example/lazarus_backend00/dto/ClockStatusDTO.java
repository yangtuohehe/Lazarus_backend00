package com.example.lazarus_backend00.dto;

import java.time.Instant;

public class ClockStatusDTO {
    private Instant currentVirtualTime;
    private boolean isRunning;
    private long stepSizeBytes; // 步长秒数
    private String stepSizeDesc; // 步长描述 (如 "1 Hour")

    // 构造函数、Getters、Setters
    public ClockStatusDTO(Instant time, boolean running, java.time.Duration step) {
        this.currentVirtualTime = time;
        this.isRunning = running;
        this.stepSizeBytes = step.getSeconds();
        this.stepSizeDesc = step.toString();
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
        isRunning = running;
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
