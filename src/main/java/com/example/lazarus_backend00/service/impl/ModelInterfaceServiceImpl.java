package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.dao.ModelInterfaceDao;
import com.example.lazarus_backend00.infrastructure.persistence.entity.ModelInterfaceEntity;
import com.example.lazarus_backend00.service.ModelInterfaceService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ModelInterfaceServiceImpl implements ModelInterfaceService {

    private final ModelInterfaceDao modelInterfaceDao;

    public ModelInterfaceServiceImpl(ModelInterfaceDao modelInterfaceDao) {
        this.modelInterfaceDao = modelInterfaceDao;
    }

    @Override
    public Integer createModelInterface(ModelInterfaceEntity modelInterface) {
        // 设置创建时间和更新时间
        LocalDateTime now = LocalDateTime.now();
        modelInterface.setCreatedAt(now);
        modelInterface.setUpdatedAt(now);

        // 插入数据库
        int rows = modelInterfaceDao.insertModelInterface(modelInterface);

        if (rows > 0) {
            // 返回自增主键
            return modelInterface.getInterfaceId();
        } else {
            return null;
        }
    }

    @Override
    public ModelInterfaceEntity getModelInterfaceById(Integer interfaceId) {
        return modelInterfaceDao.selectById(interfaceId);
    }
}