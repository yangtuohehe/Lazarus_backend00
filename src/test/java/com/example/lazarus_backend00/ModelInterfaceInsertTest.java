package com.example.lazarus_backend00;

import com.example.lazarus_backend00.infrastructure.persistence.entity.ModelInterfaceEntity;
import com.example.lazarus_backend00.service.ModelInterfaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

@SpringBootTest
public class ModelInterfaceInsertTest {

    @Autowired
    private ModelInterfaceService modelInterfaceService;

    @Test
    public void testInsertModelInterface() {

        // 创建对象
        ModelInterfaceEntity entity = new ModelInterfaceEntity();
        entity.setInterfaceId(null);
        entity.setProcessmodelId(9);
        entity.setInterfaceName("测试接口");
        entity.setInterfaceType("default");
        entity.setIsDefault(true);
        entity.setCreatedAt(null);
        entity.setUpdatedAt(null);
        entity.setInterfaceSummary("测试默认接口");

        // 测试中手动赋予创建时间和更新时间
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        // 调用接口入库
        Integer newId = modelInterfaceService.createModelInterface(entity);

        System.out.println("插入成功，生成 ID = " + newId);
    }
}