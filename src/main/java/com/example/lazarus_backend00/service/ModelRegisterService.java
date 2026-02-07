package com.example.lazarus_backend00.service;

import com.example.lazarus_backend00.infrastructure.persistence.entity.*;
import com.example.lazarus_backend00.domain.axis.Axis;

import java.util.List;

/**
 * 模型整体入库（包含：模型 + 接口 + 参数 + 动态轴定义 + 特征）
 */
public interface ModelRegisterService {

    /**
     * 新增一个完整模型。
     *
     * @param processModel 动态流程模型实体
     * @param modelInterface 模型接口实体
     * @param parameters 参数实体列表
     * @param features 每个参数对应的 Feature 列表 (按外层 index 对应 parameters)
     * @param axis 每个参数对应的时空轴列表 (统一命名为 axis)
     * @return 新增模型的 id
     */
    Integer registerModel(
            DynamicProcessModelEntity processModel,
            ModelInterfaceEntity modelInterface,
            List<ParameterEntity> parameters,
            List<List<FeatureEntity>> features,
            List<List<Axis>> axis
    );

    /**
     * 为已有的模型扩展一个新的接口及其附属数据。
     */
    Integer addInterfaceToModel(
            Integer processmodelId,
            ModelInterfaceEntity modelInterface,
            List<ParameterEntity> parameters,
            List<List<FeatureEntity>> features,
            List<List<Axis>> axis
    );
}