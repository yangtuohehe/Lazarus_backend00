package com.example.lazarus_backend00;

import com.example.lazarus_backend00.service.ModelSelectService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ModelInstantiationTest {

    @Autowired
    private ModelSelectService modelSelectService;

    @Test
    public void testModelJsonFromDatabase() {
        Integer modelId = 2;
        System.out.println("\n🚀 开始执行模型 [ID=" + modelId + "] 的 JSON 原生体检...\n");

        // 1. 调用你刚发的接口，拿到原生 JSON 字符串
        String jsonString = modelSelectService.selectModelById(modelId);

        if (jsonString == null || jsonString.isEmpty()) {
            System.err.println("❌ 数据库里没有查到 ID=2 的模型数据，请检查数据库！");
            return;
        }

        System.out.println("================ 📦 数据库原生 JSON ================");
        System.out.println("请仔细看下面这段 JSON，找找里面有没有 axisList，有没有时间轴的字段：");
        System.out.println(jsonString);
        System.out.println("====================================================\n");

        // 2. 用 Jackson 解析一下，看看里面到底有没有时间轴的信息
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonString);

            // 简单打印一下树形结构，证明 JSON 里确实有数据
            System.out.println("✅ JSON 解析成功！如果你在上面的原生 JSON 里看到了 count 和 resolution，");
            System.out.println("   那就 100% 证实了是 Jackson 反序列化 List<Axis> 时的多态丢失问题！");

        } catch (Exception e) {
            System.err.println("❌ JSON 解析失败: " + e.getMessage());
        }
    }
}