package com.example.lazarus_backend00;

import com.example.lazarus_backend00.dto.ModelContainerDTO;
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
        System.out.println("=====================================================");
        System.out.println("🚀 正在启动 Spring Boot 后端环境并开启【终极追踪模式】...");
        System.out.println("=====================================================");

        ConfigurableApplicationContext context = SpringApplication.run(LazarusBackend00Application.class, args);
        String port = context.getEnvironment().getProperty("server.port", "8080");
        String baseUrl = "http://localhost:" + port;
        RestTemplate restTemplate = new RestTemplate();
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n⏳ 正在初始化：加载真实模型 (ID=2) 并注入容器池...");
        try {
            ResponseEntity<ModelContainerDTO> initRes = restTemplate.postForEntity(
                    baseUrl + "/api/container/register?modelId=2", null, ModelContainerDTO.class);
            if (initRes.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ 模型就绪: " + initRes.getBody().getModelName());
            }
        } catch (Exception e) {}

        ResponseEntity<JsonNode> statusRes = restTemplate.getForEntity(baseUrl + "/api/clock/status", JsonNode.class);
        Instant sysTime = Instant.parse(statusRes.getBody().get("currentVirtualTime").asText());

        System.out.println("\n🕹️ 真实 AI 联动测试模式已开启！初始时间: " + sysTime);

        while (true) {
            System.out.print("\n🕒 当前时刻: [" + sysTime + "]\n👉 请输入要步进的小时数，或输入 'exit' 退出: ");
            String input = scanner.nextLine().trim();
            if ("exit".equalsIgnoreCase(input)) break;
            if (input.isEmpty()) continue;

            int hoursToStep = Integer.parseInt(input);
            sysTime = sysTime.plus(hoursToStep, ChronoUnit.HOURS);

            System.out.println("\n=========================================================");
            System.out.println("🚀 开始全链路追踪: 推进至 [" + sysTime + "]");
            System.out.println("=========================================================");

            try {
                System.out.println("\n[节点 1] 重置上帝时钟...");
                restTemplate.postForEntity(baseUrl + "/api/clock/reset?startTime=" + sysTime + "&stepHours=" + hoursToStep, null, String.class);

                System.out.println("\n[节点 2] 触发数据子系统...");
                System.out.println("   💡 请立刻查看 Spring Boot 控制台！");
                System.out.println("   💡 期望看到大量 [Debug-抽取判定] 日志。如果全是“积攒步数不足”，说明步进时间太短，请再多推几十个小时！");
                restTemplate.postForEntity(baseUrl + "/api/v1/data-subsystem/sync?time=" + sysTime, null, String.class);

                System.out.println("\n[节点 3] 监听数据推送 (等待 2 秒)...");
                System.out.println("   💡 期望看到: [HTTP 推送] 正在向 orchestration/notify-batch 发送数据。");
                System.out.println("   💡 期望看到: [接口1-Notify] 收到数据变更批量通知。");
                Thread.sleep(2000);

                System.out.println("\n[节点 4] 监控模型唤醒队列...");
                for (int i = 1; i <= 3; i++) {
                    ResponseEntity<JsonNode> taskRes = restTemplate.getForEntity(baseUrl + "/api/v1/orchestration/tasks/active", JsonNode.class);
                    JsonNode tasks = taskRes.getBody();
                    if (tasks != null && tasks.size() > 0) {
                        System.out.println("   🔥 发现被唤醒的模型任务！详情: " + tasks.toString());
                    } else {
                        System.out.println("   👀 " + i + "秒: 任务队列为空...");
                    }
                    Thread.sleep(1000);
                }

                System.out.println("\n[节点 5] 尝试落盘 AOP JSON 日志...");
                try {
                    restTemplate.postForEntity(baseUrl + "/api/log/flush", null, String.class);
                    System.out.println("   ✅ 日志写入指令已发送");
                } catch (Exception e) {}

            } catch (Exception e) {
                System.err.println("❌ 严重网络错误: " + e.getMessage());
            }
        }
        scanner.close(); context.close();
    }
}