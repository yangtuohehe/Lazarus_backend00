package com.example.lazarus_backend00.dao;
import com.example.lazarus_backend00.infrastructure.persistence.entity.FeatureEntity;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface FeatureDao {

    /**
     * 插入 Feature
     * @param featureEntity Feature 实体
     * @return 影响行数
     */
    int insertFeature(FeatureEntity featureEntity);

    /**
     * 根据ID查询 Feature
     */
    FeatureEntity selectById(Integer featureId);
}