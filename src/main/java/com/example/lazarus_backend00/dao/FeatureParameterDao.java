package com.example.lazarus_backend00.dao;

import com.example.lazarus_backend00.infrastructure.persistence.entity.FeatureParameterEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * FeatureParameterMapper
 *
 * 数据访问接口，用于操作 feature_parameter 中间表
 * 提供插入记录和根据实体属性动态查询功能
 */
@Mapper
public interface FeatureParameterDao {

    /**
     * 插入一条 FeatureParameter 记录
     *
     * @param entity FeatureParameterEntity 对象，包含 featureId、parameterId、featureLayer
     * @return int 受影响的行数（通常为 1）
     */
    int insert(FeatureParameterEntity entity);

    /**
     * 根据 FeatureParameterEntity 的非空字段动态查询
     *
     * 查询规则：
     * - 仅使用 entity 中非空字段作为 WHERE 条件
     * - featureId、parameterId、featureLayer 可组合查询
     *
     * @param entity FeatureParameterEntity 对象，非空字段作为查询条件
     * @return List<FeatureParameterEntity> 查询结果列表，可能为空
     */
    List<FeatureParameterEntity> selectByDynamicAttributes(FeatureParameterEntity entity);
}