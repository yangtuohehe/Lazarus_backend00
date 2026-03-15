package com.example.lazarus_backend00;

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
@Transactional // 测试结束后自动回滚，不污染数据库
public class ModelRegisterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("双模式测试：支持代码内JSON或读取外部JSON文件")
    void testRegisterWithSwitchableJson() throws Exception {

        // ==========================================
        // ⚙️ 配置区域
        // ==========================================

        // 【开关】true = 读取外部 JSON 文件; false = 使用下方代码中的 JSON 字符串
        boolean USE_EXTERNAL_JSON = true;

        // 1. ONNX 模型文件路径 (必填)
        String modelFilePath = "D:\\CODE\\project\\Lazarus\\Data\\Meiji(1)-water\\Meiji\\model_meiji\\tem05\\seqLen24_lead12.pt";

        // 2. 外部 JSON 文件路径 (仅当 USE_EXTERNAL_JSON = true 时生效)
        // 你可以将前端发送的请求 Body 保存为 .json 文件放在这里
        String jsonFilePath = "D:\\CODE\\project\\Lazarus\\test_payload.json";


        // ==========================================
        // 🏗️ 准备模型文件 (Model File)
        // ==========================================
        Path modelPath = Paths.get(modelFilePath);
        if (!Files.exists(modelPath)) {
            throw new RuntimeException("❌ 测试终止：找不到模型文件 -> " + modelFilePath);
        }
        System.out.println(">>> [1/3] 读取模型文件: " + modelFilePath);

        MockMultipartFile modelFilePart = new MockMultipartFile(
                "modelFile",
                "test_model.onnx",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                Files.readAllBytes(modelPath)
        );


        // ==========================================
        // 📝 准备 JSON 数据 (Payload)
        // ==========================================
        String finalJsonPayload;

        if (USE_EXTERNAL_JSON) {
            // --- 模式 A: 读取外部文件 ---
            Path jsonPath = Paths.get(jsonFilePath);
            if (!Files.exists(jsonPath)) {
                throw new RuntimeException("❌ 测试终止：开启了外部JSON模式，但找不到文件 -> " + jsonFilePath);
            }
            // 读取文件内容
            finalJsonPayload = Files.readString(jsonPath, StandardCharsets.UTF_8);
            System.out.println(">>> [2/3] 使用外部 JSON 文件: " + jsonFilePath);

        } else {
            // --- 模式 B: 使用硬编码字符串 ---
            System.out.println(">>> [2/3] 使用代码内硬编码 JSON");
            finalJsonPayload = """
                {
                    "dynamicProcessModel": {
                        "modelName": "流速v-测试",
                        "modelSourcePaper": "无",
                        "modelAuthor": "单元测试",
                        "modelSummary": "这是代码内硬编码的测试数据",
                        "version": 1
                    },
                    "modelInterface": {
                        "interfaceName": "默认接口",
                        "interfaceSummary": "自动生成",
                        "default": true,
                        "parameters": [
                            {
                                "ioType": "INPUT",
                                "tensorOrder": 0,
                                "originPointLon": 115.0,
                                "originPointLat": 10.0,
                                "axis": [
                                    { "type": "TIME", "dimensionIndex": 1, "count": 1, "resolution": 1, "unit": "hour" },
                                    { "type": "SPACE_Y", "dimensionIndex": 3, "count": 24, "resolution": 0.009, "unit": "degree" },
                                    { "type": "SPACE_X", "dimensionIndex": 4, "count": 24, "resolution": 0.009, "unit": "degree" }
                                ],
                                "features": [ { "featureName": "v" }, { "featureName": "v05" } ]
                            },
                            {
                                "ioType": "OUTPUT",
                                "tensorOrder": 0,
                                "originPointLon": 115.0,
                                "originPointLat": 10.0,
                                "axis": [
                                    { "type": "TIME", "dimensionIndex": 1, "count": 36, "resolution": 1, "unit": "hour" },
                                    { "type": "SPACE_Y", "dimensionIndex": 3, "count": 24, "resolution": 0.009, "unit": "degree" },
                                    { "type": "SPACE_X", "dimensionIndex": 4, "count": 24, "resolution": 0.009, "unit": "degree" }
                                ],
                                "features": [ { "featureName": "v" } ]
                            }
                        ]
                    }
                }
                """;
        }

        // 构建 Payload Part
        MockMultipartFile payloadPart = new MockMultipartFile(
                "payload",
                "",
                "application/json", // 关键：告诉后端这是 JSON
                finalJsonPayload.getBytes(StandardCharsets.UTF_8)
        );


        // ==========================================
        // 🚀 发送请求
        // ==========================================
        System.out.println(">>> [3/3] 正在发送模拟请求...");

        mockMvc.perform(multipart("/model/register")
                        .file(modelFilePart)
                        .file(payloadPart))
                .andDo(print()) // 打印请求和响应详情
                .andExpect(status().isOk()); // 断言状态码 200

        System.out.println("====== ✅ 测试通过 (事务已自动回滚) ======");
    }
}