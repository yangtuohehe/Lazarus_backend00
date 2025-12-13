package com.example.lazarus_backend00.service;

import com.example.lazarus_backend00.infrastructure.persistence.entity.ModelInterfaceEntity;

/**
 * 模型接口服务接口
 */
public interface ModelInterfaceService {

    /**
     * 新增一个模型接口
     *
     * 说明：
     * - interfaceId 将被忽略
     * - createdAt / updatedAt 由 Service 层统一设置
     *
     * @param modelInterface 模型接口数据库实体
     * @return 新增接口ID
     */
    Integer createModelInterface(ModelInterfaceEntity modelInterface);

    /**
     * 根据ID查询模型接口
     *
     * @param interfaceId 接口ID
     * @return ModelInterfaceEntity
     */
    ModelInterfaceEntity getModelInterfaceById(Integer interfaceId);
}

