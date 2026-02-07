package com.example.lazarus_backend00.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.n52.jackson.datatype.jts.JtsModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /**
     * ✅ 推荐做法：
     * 只需定义 JtsModule 的 Bean。
     * Spring Boot 会自动把它捡起来，注入到那个已经配置好时间处理的默认 ObjectMapper 中。
     */
    @Bean
    public JtsModule jtsModule() {
        return new JtsModule();
    }
}