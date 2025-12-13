package com.example.lazarus_backend00.dao;

import com.example.lazarus_backend00.infrastructure.persistence.entity.ParameterEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ParameterDao {

    /**
     * 插入 Parameter
     * @param parameterEntity 参数实体
     * @return 影响行数
     */
    int insertParameter(ParameterEntity parameterEntity);

    /**
     * 根据 ID 查询 Parameter
     */
    ParameterEntity selectById(Integer parameterId);
}
