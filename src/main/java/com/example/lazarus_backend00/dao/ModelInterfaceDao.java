package com.example.lazarus_backend00.dao;

import com.example.lazarus_backend00.infrastructure.persistence.entity.ModelInterfaceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelInterfaceDao {

    /**
     * 插入 ModelInterface
     * @param modelInterfaceEntity ModelInterface 实体
     * @return 影响行数
     */
    int insertModelInterface(ModelInterfaceEntity modelInterfaceEntity);

    /**
     * 根据ID查询 ModelInterface
     * @param interfaceId 接口ID
     * @return ModelInterface 实体
     */
    ModelInterfaceEntity selectById(Integer interfaceId);
}