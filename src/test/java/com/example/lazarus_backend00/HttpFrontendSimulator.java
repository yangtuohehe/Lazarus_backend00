package com.example.lazarus_backend00;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Scanner;

public class HttpFrontendSimulator {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(LazarusBackend00Application.class, args);
        String port = context.getEnvironment().getProperty("server.port", "8080");
        String baseUrl = "http://localhost:" + port;
        RestTemplate restTemplate = new RestTemplate();
        Scanner scanner = new Scanner(System.in);

        // 初始化注册模型
        try {
            restTemplate.postForEntity(baseUrl + "/api/container/register?modelId=2", null, String.class);
        } catch (Exception e) {}

        ResponseEntity<JsonNode> statusRes = restTemplate.getForEntity(baseUrl + "/api/clock/status", JsonNode.class);
        Instant sysTime = Instant.parse(statusRes.getBody().get("currentVirtualTime").asText());

        while (true) {
            System.out.print("\n👉 输入步进小时数 (当前 " + sysTime + ")，或 'Q' 退出: ");
            String input = scanner.nextLine().trim();
            if ("Q".equalsIgnoreCase(input)) break;
            if (input.isEmpty()) continue;

            try {
                int hoursToStep = Integer.parseInt(input);
                sysTime = sysTime.plus(hoursToStep, ChronoUnit.HOURS);

                // 1. 步进时钟
                restTemplate.postForEntity(baseUrl + "/api/clock/reset?startTime=" + sysTime + "&stepHours=" + hoursToStep, null, String.class);

                // 2. 触发数据子系统同步 (这会瞬间触发广播并打印状态，不需要等3秒！)
                restTemplate.postForEntity(baseUrl + "/api/v1/data-subsystem/sync?time=" + sysTime, null, String.class);

            } catch (Exception e) {
                System.err.println("❌ 输入错误或网络异常");
            }
        }
        scanner.close(); context.close();
    }
}