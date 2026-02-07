package com.example.lazarus_backend00;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// 静态导入，简化代码
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
public class DebugJsonStructureTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("【必杀技】获取后端标准JSON结构")
    void getAndPrintStandardJson() throws Exception {

        System.out.println("\n\n▼▼▼▼▼▼▼▼▼▼▼▼ 标准 JSON 开始 (请复制下方内容) ▼▼▼▼▼▼▼▼▼▼▼▼\n");

        // 发送 GET 请求到 /model/structure
        mockMvc.perform(get("/model/structure")
                        .accept(MediaType.APPLICATION_JSON))

                // 1. 验证状态码 200，确保接口写对了
                .andExpect(status().isOk())

                // 2. 顺便验证一下关键字段，确保 DTO 里的值没丢
                .andExpect(jsonPath("$.dynamicProcessModel.version", is(1))) // 验证 version 是数字 1
                .andExpect(jsonPath("$.modelInterface.parameters[0].tensorOrder", is(0))) // 验证 tensorOrder 是数字 0

                // 3. 核心：将响应体打印到控制台
                .andDo(print());

        System.out.println("\n▲▲▲▲▲▲▲▲▲▲▲▲ 标准 JSON 结束 ▲▲▲▲▲▲▲▲▲▲▲▲\n\n");
    }
}
