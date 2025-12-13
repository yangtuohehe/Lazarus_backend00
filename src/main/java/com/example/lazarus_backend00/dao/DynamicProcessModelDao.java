package com.example.lazarus_backend00.dao;

import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DynamicProcessModelDao {

    /**
     * 插入 DynamicProcessModel
     *
     * @param dynamicProcessModelEntity 实体对象
     * @return 影响行数
     */
    int insertDynamicProcessModel(DynamicProcessModelEntity dynamicProcessModelEntity);

    /**
     * 根据ID查询 DynamicProcessModel
     *
     * @param processmodelId 模型ID
     * @return DynamicProcessModelEntity
     */
    DynamicProcessModelEntity selectById(Integer processmodelId);
}