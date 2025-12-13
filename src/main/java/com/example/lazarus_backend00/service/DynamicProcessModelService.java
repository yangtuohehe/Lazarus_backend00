package com.example.lazarus_backend00.service;

import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;

import java.time.LocalDateTime;

/**
 * 动态流程模型服务接口
 */
public interface DynamicProcessModelService {

    /**
     * 新增一个动态流程模型
     *
     * 说明：
     * - processmodelId 将被忽略
     * - createdAt / updatedAt 由 Service 层统一设置
     *
     * @param dynamicProcessModel 动态流程模型数据库实体
     * @return 新增模型ID
     */
    Integer createDynamicProcessModel(DynamicProcessModelEntity dynamicProcessModel);
}