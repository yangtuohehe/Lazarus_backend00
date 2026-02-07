package com.example.lazarus_backend00.infrastructure.config;
import com.example.lazarus_backend00.component.orchestration.ModelEventTrigger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

@Configuration
public class ModelConfig {

    // 自动读取 application.properties 中的 simulation.start-time
    // 如果配置文件里没写，冒号后面是默认值 (这里默认为 Unix 纪元时间)
    @Value("${simulation.start-time:1970-01-01T00:00:00Z}")
    private String startTimeStr;

    @Bean
    public ModelEventTrigger modelEventTrigger() {
        // 解析字符串为 Instant 对象
        Instant virtualStartTime = Instant.parse(startTimeStr);
        return new ModelEventTrigger(virtualStartTime);
    }

}