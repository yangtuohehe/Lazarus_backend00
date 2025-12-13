package com.example.lazarus_backend00.service;

import com.example.lazarus_backend00.infrastructure.persistence.entity.FeatureEntity;

public interface FeatureService {


    /**
     * 新增 Feature（Entity 由调用方传入）
     * 说明：
     * - featureId 将被忽略
     * - createdAt / updatedAt 由 Service 统一处理
     */
    Integer createFeature(FeatureEntity entity);
}