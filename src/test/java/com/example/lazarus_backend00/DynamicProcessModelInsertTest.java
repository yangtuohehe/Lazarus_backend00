package com.example.lazarus_backend00;

import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;
import com.example.lazarus_backend00.service.DynamicProcessModelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@SpringBootTest
public class DynamicProcessModelInsertTest {

    @Autowired
    private DynamicProcessModelService dynamicProcessModelService;

    @Test
    public void testInsertDynamicProcessModel() throws Exception {

        // ====== 1. 创建实体对象 ======
        DynamicProcessModelEntity entity = new DynamicProcessModelEntity();
        entity.setProcessmodelId(null);  // 主键由数据库生成

        entity.setModelName("测试模型");
        entity.setModelSourcePaper("测试来源");
        entity.setModelAuthor("作者1、作者2");
        entity.setModelSummary(
                "这是一个测试模型，输入一个张量，输出一个张量。模型结构{\n" +
                        "cnn(),\n" +
                        "vnn(),\n" +
                        "mlp(),\n" +
                        "cnn(),\n" +
                        "mlp\n" +
                        "}"
        );

        // ====== 2. 加载文件并转为 byte[] ======
        // 你可以替换为你项目中的任意文件路径
        Path filePath = Path.of("D:\\CODE\\project\\Lazarus\\Lazarus-数据处理\\output\\channel_sum.onnx");
        byte[] bytes = Files.readAllBytes(filePath);
        entity.setModelFile(bytes);

        // ====== 3. 创建时间 & 修改时间（你明确要求测试中赋值） ======
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        // ====== 4. 调用 Service 入库 ======
        Integer newId = dynamicProcessModelService.createDynamicProcessModel(entity);

        System.out.println("动态模型插入成功，生成 ID = " + newId);
    }
}
