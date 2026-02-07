package com.example.lazarus_backend00.dao;

import com.example.lazarus_backend00.infrastructure.persistence.entity.ModelInterfaceEntity;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ModelInterfaceDao {

    /**
     * 插入
     */
    int insert(ModelInterfaceEntity modelInterfaceEntity);

    /**
     * 根据ID查询
     */
    ModelInterfaceEntity selectById(Integer id);

    /**
     * 根据属性动态查询
     */
    List<ModelInterfaceEntity> selectByCondition(ModelInterfaceEntity entity);
}