package com.example.lazarus_backend00.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lazarus.model-pool") // 确保这里的 prefix 和 properties 中一致
public class ModelPoolConfig {

    private int maxConcurrentRuns = 4;
    private long maxMemoryMb = 8192;
    private int waitTimeoutSeconds = 300;

    // 🔥 新增：最小可用内存阈值 (默认 2000 MB)
    private long minFreeMemoryMb = 2000;

    public int getMaxConcurrentRuns() { return maxConcurrentRuns; }
    public void setMaxConcurrentRuns(int maxConcurrentRuns) { this.maxConcurrentRuns = maxConcurrentRuns; }

    public long getMaxMemoryMb() { return maxMemoryMb; }
    public void setMaxMemoryMb(long maxMemoryMb) { this.maxMemoryMb = maxMemoryMb; }

    public int getWaitTimeoutSeconds() { return waitTimeoutSeconds; }
    public void setWaitTimeoutSeconds(int waitTimeoutSeconds) { this.waitTimeoutSeconds = waitTimeoutSeconds; }

    // 🔥 新增 Getter 和 Setter
    public long getMinFreeMemoryMb() { return minFreeMemoryMb; }
    public void setMinFreeMemoryMb(long minFreeMemoryMb) { this.minFreeMemoryMb = minFreeMemoryMb; }
}