package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.dao.FeatureDao;
import com.example.lazarus_backend00.infrastructure.persistence.entity.FeatureEntity;
import com.example.lazarus_backend00.service.FeatureService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class FeatureServiceImpl implements FeatureService {


    private final FeatureDao featureDao;

    public FeatureServiceImpl(FeatureDao featureDao) {
        this.featureDao = featureDao;
    }

    @Override
    public Integer createFeature(FeatureEntity entity) {

        // ✅ 强制忽略外部可能传入的 ID
        entity.setFeatureId(null);

        // ✅ 时间统一由 Service 层控制
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        featureDao.insertFeature(entity);

        return entity.getFeatureId();
    }
}