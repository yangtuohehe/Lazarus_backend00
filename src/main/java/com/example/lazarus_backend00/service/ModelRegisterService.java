package com.example.lazarus_backend00.service;

import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;
import com.example.lazarus_backend00.infrastructure.persistence.entity.FeatureEntity;
import com.example.lazarus_backend00.infrastructure.persistence.entity.ModelInterfaceEntity;
import com.example.lazarus_backend00.infrastructure.persistence.entity.ParameterEntity;

import java.util.List;

/**
 * 模型整体入库（包含：模型 + 接口 + 参数 + 特征）
 */
public interface ModelRegisterService {

    /**
     * 新增一个完整模型。
     *
     * 入库顺序：
     * 1. DynamicProcessModel
     * 2. ModelInterface
     * 3. 多个 Parameter + 对应 Feature
     *
     * 要求：
     * - 所有 createdAt / updatedAt 由 Service 层统一设置
     * - 参数数量不固定
     * - 整个过程须开启事务
     *
     * @param processModel 动态流程模型实体
     * @param modelInterface 模型接口实体
     * @param parameters 参数实体列表（数量不固定）
     * @param features 每个参数对应的 Feature 列表，按顺序对应参数列表
     * @return 新增模型的 processmodelId
     */
    Integer registerModel(
            DynamicProcessModelEntity processModel,
            ModelInterfaceEntity modelInterface,
            List<ParameterEntity> parameters,
            List<List<FeatureEntity>> features
    );

    /**
     * 为已有的模型扩展一个新的接口与参数/特征。
     *
     * 使用场景：
     * - 一个模型可以拥有多个接口
     * - 每个接口下可对应多个参数与特征
     *
     * 入库顺序：
     * 1. 新增一个 ModelInterface（绑定已有的 processmodelId）
     * 2. 新增多个 Parameter（绑定该接口）
     * 3. 新增对应的 Feature（绑定 Parameter）
     *
     * @param processmodelId 已存在模型的 ID
     * @param modelInterface 新的接口实体
     * @param parameters 新接口对应的参数列表（数量不定）
     * @param features 每个参数对应的 Feature 列表，按顺序对应 parameters
     * @return 新增的 interfaceId
     */
    Integer addInterfaceToModel(
            Integer processmodelId,
            ModelInterfaceEntity modelInterface,
            List<ParameterEntity> parameters,
            List<List<FeatureEntity>> features
    );
}