package com.example.lazarus_backend00.dao;

import com.example.lazarus_backend00.infrastructure.persistence.entity.FeatureEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface FeatureDao {

    /**
     * 插入 Feature
     */
    int insert(FeatureEntity featureEntity);

    /**
     * 根据ID查询 Feature
     */
    FeatureEntity selectById(Integer id);

    /**
     * 动态条件查询
     */
    List<FeatureEntity> selectByCondition(FeatureEntity entity);
}