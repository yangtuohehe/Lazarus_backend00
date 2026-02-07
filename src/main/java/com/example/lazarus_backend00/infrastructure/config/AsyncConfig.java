package com.example.lazarus_backend00.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：建议根据 CPU 核心数配置，例如 CPU * 2
        executor.setCorePoolSize(10);
        // 最大线程数：决定了系统的最大并发任务容量
        executor.setMaxPoolSize(50);
        // 队列容量：缓冲队列
        executor.setQueueCapacity(200);
        // 线程前缀
        executor.setThreadNamePrefix("Lazarus-Worker-");
        // 拒绝策略：当队列满了，由调用者线程执行（降低速度但保证不丢任务）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}