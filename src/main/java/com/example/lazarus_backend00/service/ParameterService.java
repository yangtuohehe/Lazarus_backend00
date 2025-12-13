package com.example.lazarus_backend00.service;

import com.example.lazarus_backend00.infrastructure.persistence.entity.ParameterEntity;

import java.time.LocalDateTime;

/**
 * 参数服务接口
 */
public interface ParameterService {

    /**
     * 新增一个参数
     *
     * 说明：
     * - parameterId 将被忽略
     * - createdAt / updatedAt 由 Service 层统一设置
     *
     * @param parameter 参数数据库实体
     * @return 新增参数ID
     */
    Integer createParameter(ParameterEntity parameter);
}
