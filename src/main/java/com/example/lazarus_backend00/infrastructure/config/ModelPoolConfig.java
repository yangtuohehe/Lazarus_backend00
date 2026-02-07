package com.example.lazarus_backend00.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * 映射 application.yml 中的 lazarus.model-pool 配置
 */
@Component
@ConfigurationProperties(prefix = "model-pool")
public class ModelPoolConfig {

    /** 最大并发数 (默认4) */
    private int maxConcurrentRuns = 4;

    /** 最大内存 MB (默认4GB) */
    private long maxMemoryMb = 400;

    public int getMaxConcurrentRuns() {
        return maxConcurrentRuns;
    }

    public void setMaxConcurrentRuns(int maxConcurrentRuns) {
        this.maxConcurrentRuns = maxConcurrentRuns;
    }

    public long getMaxMemoryMb() {
        return maxMemoryMb;
    }

    public void setMaxMemoryMb(long maxMemoryMb) {
        this.maxMemoryMb = maxMemoryMb;
    }

    public int getWaitTimeoutSeconds() {
        return waitTimeoutSeconds;
    }

    public void setWaitTimeoutSeconds(int waitTimeoutSeconds) {
        this.waitTimeoutSeconds = waitTimeoutSeconds;
    }

    /** 排队等待超时时间 (默认5分钟) */
    private int waitTimeoutSeconds = 300;
}
