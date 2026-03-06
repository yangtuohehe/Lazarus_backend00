package com.example.lazarus_backend00.infrastructure.config;

import com.example.lazarus_backend00.component.orchestration.ModelEventTrigger;
import com.example.lazarus_backend00.service.ModelOrchestratorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

@Configuration
public class ModelTriggerConfig {

    // 🔥 这里的 @Value 会精准读取你 application.properties 里的值
    @Value("${simulation.start-time:1970-01-01T00:00:00Z}")
    private String startTimeStr;

    @Bean
    public ModelEventTrigger modelEventTrigger(ModelOrchestratorService orchestratorService) {
        // 将读取到的字符串解析为时间对象
        Instant virtualStartTime = Instant.parse(startTimeStr);
        // 将服务和时间一起传给触发器的构造函数
        return new ModelEventTrigger(orchestratorService, virtualStartTime);
    }
}