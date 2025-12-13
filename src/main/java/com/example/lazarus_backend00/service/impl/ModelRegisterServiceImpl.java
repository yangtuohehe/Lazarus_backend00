package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.dao.DynamicProcessModelDao;
import com.example.lazarus_backend00.dao.ModelInterfaceDao;
import com.example.lazarus_backend00.dao.ParameterDao;
import com.example.lazarus_backend00.dao.FeatureDao;
import com.example.lazarus_backend00.infrastructure.persistence.entity.*;
import com.example.lazarus_backend00.service.ModelRegisterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ModelRegisterServiceImpl implements ModelRegisterService {

    private final DynamicProcessModelDao dynamicProcessModelDao;
    private final ModelInterfaceDao modelInterfaceDao;
    private final ParameterDao parameterDao;
    private final FeatureDao featureDao;

    public ModelRegisterServiceImpl(
            DynamicProcessModelDao dynamicProcessModelDao,
            ModelInterfaceDao modelInterfaceDao,
            ParameterDao parameterDao,
            FeatureDao featureDao) {

        this.dynamicProcessModelDao = dynamicProcessModelDao;
        this.modelInterfaceDao = modelInterfaceDao;
        this.parameterDao = parameterDao;
        this.featureDao = featureDao;
    }


    private void insertParametersAndFeatures(
            Integer interfaceId,
            List<ParameterEntity> parameters,
            List<List<FeatureEntity>> features,
            LocalDateTime now
    ) {
        for (int i = 0; i < parameters.size(); i++) {

            // === 1. 插入 Parameter ===
            ParameterEntity param = parameters.get(i);
            param.setInterfaceId(interfaceId);
            param.setCreatedAt(now);
            param.setUpdatedAt(now);

            parameterDao.insertParameter(param);
            Integer parameterId = param.getParameterId();   // ★ 必须拿到 ID

            // === 2. 插入 Feature 列表 ===
            List<FeatureEntity> feats = features.get(i);

            for (FeatureEntity feat : feats) {
                feat.setParameterId(parameterId);   // ★ 外键关键点
                feat.setCreatedAt(now);
                feat.setUpdatedAt(now);
                featureDao.insertFeature(feat);
            }
        }
    }

    // -------------------------------------------------------------------------
    // 1. 创建新模型（模型+接口+参数+特征）
    // -------------------------------------------------------------------------
    @Override
    @Transactional
    public Integer registerModel(
            DynamicProcessModelEntity processModel,
            ModelInterfaceEntity modelInterface,
            List<ParameterEntity> parameters,
            List<List<FeatureEntity>> features){

        LocalDateTime now = LocalDateTime.now();

        // 1. 插入模型
        processModel.setCreatedAt(now);
        processModel.setUpdatedAt(now);
        dynamicProcessModelDao.insertDynamicProcessModel(processModel);
        Integer processmodelId = processModel.getProcessmodelId();

        // 2. 插入接口
        modelInterface.setProcessmodelId(processmodelId);
        modelInterface.setCreatedAt(now);
        modelInterface.setUpdatedAt(now);
        modelInterfaceDao.insertModelInterface(modelInterface);
        Integer interfaceId = modelInterface.getInterfaceId();

        // 3. 公共插入 Parameter + Feature
        insertParametersAndFeatures(interfaceId, parameters, features, now);

        return processmodelId;
    }

    // -------------------------------------------------------------------------
    // 2. 为现有模型添加新的接口 + 参数 + 特征
    // -------------------------------------------------------------------------
    @Override
    @Transactional
    public Integer addInterfaceToModel(
            Integer processmodelId,
            ModelInterfaceEntity modelInterface,
            List<ParameterEntity> parameters,
            List<List<FeatureEntity>> features) {

        LocalDateTime now = LocalDateTime.now();

        // 1. 插入接口
        modelInterface.setProcessmodelId(processmodelId);
        modelInterface.setCreatedAt(now);
        modelInterface.setUpdatedAt(now);
        modelInterfaceDao.insertModelInterface(modelInterface);
        Integer interfaceId = modelInterface.getInterfaceId();

        // 2. 公共插入 Parameter + Feature
        insertParametersAndFeatures(interfaceId, parameters, features, now);

        return interfaceId;
    }
}