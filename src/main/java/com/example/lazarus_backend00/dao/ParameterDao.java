package com.example.lazarus_backend00.dao;

import com.example.lazarus_backend00.infrastructure.persistence.entity.ParameterEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ParameterDao {

    /**
     * 插入
     */
    int insert(ParameterEntity parameterEntity);

    /**
     * 根据 ID 查询
     */
    ParameterEntity selectById(Integer id);

    /**
     * 动态条件查询
     * (去掉 @Param)
     */
    List<ParameterEntity> selectByCondition(ParameterEntity entity);
}