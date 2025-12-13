package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.dao.DynamicProcessModelDao;
import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;
import com.example.lazarus_backend00.service.DynamicProcessModelService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DynamicProcessModelServiceImpl implements DynamicProcessModelService {

    private final DynamicProcessModelDao dynamicProcessModelDao;

    public DynamicProcessModelServiceImpl(DynamicProcessModelDao dynamicProcessModelDao) {
        this.dynamicProcessModelDao = dynamicProcessModelDao;
    }

    @Override
    public Integer createDynamicProcessModel(DynamicProcessModelEntity entity) {
        // 强制忽略传入的主键
        entity.setProcessmodelId(null);

        // 生成创建 / 更新时间
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);


        // 调用 DAO 入库
        dynamicProcessModelDao.insertDynamicProcessModel(entity);

        // useGeneratedKeys 会自动回填 processmodelId
        return entity.getProcessmodelId();
    }
}