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

        System.out.println("🚀 启动模拟前端客户端...");

        // 🎯 定义需要自动加载和注册的模型 ID 列表
        int[] modelIdsToRegister = {2,3};

        // 1. 初始化注册模型
        for (int modelId : modelIdsToRegister) {
            try {
                System.out.println("📦 正在尝试加载模型 (id=" + modelId + ")...");
                restTemplate.postForEntity(baseUrl + "/api/container/register?modelId=" + modelId, null, String.class);
                System.out.println("✅ 模型(id=" + modelId + ")加载命令已发送。");
            } catch (Exception e) {
                System.err.println("⚠️ 模型(id=" + modelId + ")加载可能已存在或出现异常: " + e.getMessage());
            }
        }

        // 2. 获取模型详细信息，以便提取 tif -> tsdatablock 所需的地理参数
        for (int modelId : modelIdsToRegister) {
            System.out.println("\n🔍 正在获取模型(id=" + modelId + ")参数信息...");
            try {
                // 注意：这里假设您有一个可以根据 modelId 查询模型详细信息的接口。
                // 例如：/api/model/detail?modelId=2 或者是之前注册时返回的结构
                // 如果您没有专门的查询接口，您可以尝试调用 /model/structure 获取标准结构（此处以结构为例演示解析过程，实际应查询具体模型）

                // TODO: 建议后续如果后端有真实的查询接口，请替换为 baseUrl + "/api/model/detail?modelId=" + modelId
                ResponseEntity<JsonNode> modelDetailRes = restTemplate.getForEntity(baseUrl + "/model/structure", JsonNode.class);

                if (modelDetailRes.getStatusCode().is2xxSuccessful() && modelDetailRes.getBody() != null) {
                    JsonNode modelInfo = modelDetailRes.getBody();

                    System.out.println("\n📊 ================= 提取到的模型(id=" + modelId + ")地理参数 ================= 📊");

                    // 导航到 parameters 节点
                    JsonNode parameters = modelInfo.path("modelInterface").path("parameters");
                    if (parameters.isArray() && parameters.size() > 0) {
                        JsonNode param = parameters.get(0); // 取第一个参数作为示例

                        // 提取原点信息
                        double originLon = param.path("originPointLon").asDouble();
                        double originLat = param.path("originPointLat").asDouble();
                        System.out.printf("📍 原点坐标: Longitude = %.6f, Latitude = %.6f\n", originLon, originLat);

                        // 提取轴信息 (分辨率、数量等)
                        JsonNode axes = param.path("axis");
                        if (axes.isArray()) {
                            for (JsonNode axis : axes) {
                                String type = axis.path("type").asText();
                                if ("SPACE_X".equals(type) || "SPACE_Y".equals(type)) {
                                    double resolution = axis.path("resolution").asDouble();
                                    int count = axis.path("count").asInt();
                                    String unit = axis.path("unit").asText();
                                    System.out.printf("📏 %s 轴: 分辨率 = %.6f %s, 节点数 = %d\n", type, resolution, unit, count);
                                }
                            }
                        }
                        System.out.println("⚠️ 请将上述地理参数原封不动地手动填入或传递给您的 tif->tsdatablock 转换函数中。");
                    } else {
                        System.out.println("❌ 未在模型信息中找到参数(parameters)节点。");
                    }
                    System.out.println("==========================================================\n");
                }
            } catch (Exception e) {
                System.err.println("❌ 获取模型(id=" + modelId + ")参数失败，请确保查询接口可用: " + e.getMessage());
            }
        }


        // 3. 获取系统初始时间
        ResponseEntity<JsonNode> statusRes = restTemplate.getForEntity(baseUrl + "/api/clock/status", JsonNode.class);
        Instant sysTime = Instant.parse(statusRes.getBody().get("currentVirtualTime").asText());

        // 4. 进入交互式时钟控制循环
        while (true) {
            System.out.print("\n👉 输入步进小时数 (当前 " + sysTime + ")，或 'Q' 退出: ");
            String input = scanner.nextLine().trim();
            if ("Q".equalsIgnoreCase(input)) break;
            if (input.isEmpty()) continue;

            try {
                int hoursToStep = Integer.parseInt(input);
                sysTime = sysTime.plus(hoursToStep, ChronoUnit.HOURS);

                System.out.println("⏱️ 推进时间至: " + sysTime);

                // 1. 步进时钟
                restTemplate.postForEntity(baseUrl + "/api/clock/reset?startTime=" + sysTime + "&stepHours=" + hoursToStep, null, String.class);

                // 2. 触发数据子系统同步 (通过 HTTP 协议)
                System.out.println("📡 通过 HTTP 触发数据子系统同步...");
                restTemplate.postForEntity(baseUrl + "/api/v1/data-subsystem/sync?time=" + sysTime, null, String.class);

            } catch (Exception e) {
                System.err.println("❌ 输入错误或网络调用异常: " + e.getMessage());
            }
        }

        System.out.println("👋 模拟器已退出。");
        scanner.close();
        context.close();
    }
}