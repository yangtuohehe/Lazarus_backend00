package com.example.lazarus_backend00;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
//@Transactional // 开启事务：测试运行完后会自动回滚，保持数据库干净。如果想保留数据，请注释掉此行。
public class BatchModelRegisterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ==========================================
    // 🔥 配置路径 (请根据你的实际项目位置修改)
    // ==========================================

    // 1. JSON 配置文件路径 (Java 生成的 models_config.json)
    // 假设在项目根目录下，或者你可以写绝对路径
    private static final String CONFIG_FILE_PATH = "D:\\CODE\\project\\Lazarus\\Lazarus-数据处理\\output\\models_config.json";

    // 2. ONNX 模型文件夹路径 (Python 生成的 generated_models 文件夹)
    // 建议写绝对路径，防止找不到
    private static final String ONNX_DIR_PATH = "D:\\CODE\\project\\Lazarus\\Lazarus-数据处理\\outputgenerated_models";

    @Test
    @DisplayName("🔥 批量读取生成的JSON和ONNX模型进行入库测试")
    void testBatchRegisterModels() throws Exception {

        System.out.println("====== 开始批量入库测试 ======");

        // 1. 读取 models_config.json
        Path jsonPath = Paths.get(CONFIG_FILE_PATH);
        if (!Files.exists(jsonPath)) {
            // 尝试在当前工程目录下找
            jsonPath = Paths.get(System.getProperty("user.dir"), CONFIG_FILE_PATH);
            if (!Files.exists(jsonPath)) {
                throw new RuntimeException("❌ 找不到配置文件: " + CONFIG_FILE_PATH + "，请先运行 Java Generator 生成配置！");
            }
        }

        System.out.println(">>> 读取配置文件: " + jsonPath.toAbsolutePath());
        JsonNode rootNode = objectMapper.readTree(jsonPath.toFile());

        if (!rootNode.isArray()) {
            throw new RuntimeException("❌ 配置文件格式错误：应该是 JSON 数组 [...]");
        }

        System.out.println(">>> 发现模型定义数量: " + rootNode.size());

        // 2. 循环遍历每个模型定义
        int successCount = 0;
        for (int i = 0; i < rootNode.size(); i++) {
            JsonNode modelNode = rootNode.get(i);

            // 2.1 提取模型名称
            String modelName = modelNode.get("dynamicProcessModel").get("modelName").asText();
            System.out.println("\n-------------------------------------------------");
            System.out.println("正在处理第 [" + (i + 1) + "] 个模型: " + modelName);

            // 2.2 推算对应的 ONNX 文件名
            // 🔥 必须与 Python 脚本的命名逻辑一致：空格转下划线，斜杠转下划线，加 .onnx
            String fileName = modelName.replace(" ", "_").replace("/", "_") + ".onnx";
            Path onnxFilePath = Paths.get(ONNX_DIR_PATH, fileName);

            // 如果相对路径找不到，尝试拼接到 user.dir
            if (!Files.exists(onnxFilePath)) {
                onnxFilePath = Paths.get(System.getProperty("user.dir"), ONNX_DIR_PATH, fileName);
            }

            // 2.3 检查文件是否存在
            if (!Files.exists(onnxFilePath)) {
                System.err.println("❌ 跳过：找不到对应的 ONNX 文件 -> " + onnxFilePath.toAbsolutePath());
                // 在严格测试中，这里可以选择 throw exception
                continue;
            }

            // 2.4 读取 ONNX 文件内容
            byte[] fileContent = Files.readAllBytes(onnxFilePath);
            MockMultipartFile modelFilePart = new MockMultipartFile(
                    "modelFile",
                    fileName,
                    MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    fileContent
            );

            // 2.5 准备 JSON Payload
            // 将 JsonNode 转回 String
            String jsonPayload = objectMapper.writeValueAsString(modelNode);
            MockMultipartFile payloadPart = new MockMultipartFile(
                    "payload",
                    "",
                    "application/json",
                    jsonPayload.getBytes(StandardCharsets.UTF_8)
            );

            // 2.6 发送请求
            try {
                mockMvc.perform(multipart("/model/register")
                                .file(modelFilePart)
                                .file(payloadPart))
                        // .andDo(print()) // 如果想看详细日志可以打开，但批量跑建议关闭，否则日志太多
                        .andExpect(status().isOk());

                System.out.println("✅ 入库成功: " + fileName);
                successCount++;
            } catch (Exception e) {
                System.err.println("❌ 入库失败: " + modelName);
                e.printStackTrace();
                throw e; // 抛出异常中断测试
            }
        }

        System.out.println("\n=================================================");
        System.out.println("🎉 批量测试完成！");
        System.out.println("应入库: " + rootNode.size());
        System.out.println("实入库: " + successCount);
        System.out.println("=================================================");
    }
}
