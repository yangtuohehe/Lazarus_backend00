package com.example.lazarus_backend00;

import com.example.lazarus_backend00.infrastructure.persistence.entity.FeatureEntity;
import com.example.lazarus_backend00.service.FeatureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

@SpringBootTest
public class FeatureInsertTest {

    @Autowired
    private FeatureService featureService;

    @Test
    public void testInsertFeature() {

        // 模拟你的 JSON 输入：
        // {
        //   "featureName": "测试输出特征值1",
        //   "dimensionIndex": 0
        // }

        FeatureEntity entity = new FeatureEntity();
        entity.setFeatureName("测试输出特征值1");
        entity.setDimensionIndex(0);

        // parameterId 是外键，你需要传一个存在于 parameter 表中的 ID
        // 测试环境你应该自己替换成数据库已有的 parameter_id
        entity.setParameterId(4);

        // 由 Service 统一处理 createdAt / updatedAt（这里设置 null）
        entity.setCreatedAt(null);
        entity.setUpdatedAt(null);

        Integer newId = featureService.createFeature(entity);

        System.out.println("Feature 插入成功，新 ID = " + newId);
    }
}
