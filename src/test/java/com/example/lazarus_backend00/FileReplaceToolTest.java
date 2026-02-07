package com.example.lazarus_backend00;

import com.example.lazarus_backend00.dao.DynamicProcessModelDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Rollback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootTest
public class FileReplaceToolTest {

    @Autowired
    private DynamicProcessModelDao dynamicProcessModelDao;

    @Test
    @Rollback(false) // ⚠️ 重要：设置为 false，否则测试跑完会回滚，修改不生效！
    public void replaceModelFile() throws IOException {

        // ================= 配置区域 =================
        Integer targetId = 10; // 🎯 你要替换的那条记录的 ID
        String newFilePath = "D:\\CODE\\project\\Lazarus\\Lazarus-数据处理\\output\\tem\\seqLen24_lead12.onnx"; // 📂 新文件的路径
        // ===========================================

        Path path = Paths.get(newFilePath);
        if (!Files.exists(path)) {
            throw new RuntimeException("❌ 文件不存在: " + newFilePath);
        }

        // 1. 读取文件为字节数组
        byte[] fileBytes = Files.readAllBytes(path);
        System.out.println(">>> 正在读取文件，大小: " + fileBytes.length + " bytes");

        // 2. 执行更新
        int rows = dynamicProcessModelDao.updateModelFile(targetId, fileBytes);

        // 3. 验证结果
        if (rows > 0) {
            System.out.println("✅ 成功！ID 为 [" + targetId + "] 的文件已被替换。");
        } else {
            System.err.println("❌ 失败！未找到 ID 为 [" + targetId + "] 的记录。");
        }
    }
}
