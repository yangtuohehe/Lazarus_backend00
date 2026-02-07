package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.dao.*;
import com.example.lazarus_backend00.infrastructure.persistence.entity.*;
import com.example.lazarus_backend00.service.ModelSelectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ModelSelectServiceImpl implements ModelSelectService {

    private final DynamicProcessModelDao dynamicProcessModelDao;
    private final ModelInterfaceDao modelInterfaceDao;
    private final ParameterDao parameterDao;
    private final FeatureDao featureDao;
    private final FeatureParameterDao featureParameterDao;
    private final TimeAxisDao timeAxisDao; // 确保注入了这个
    private final ObjectMapper objectMapper;

    public ModelSelectServiceImpl(
            DynamicProcessModelDao dynamicProcessModelDao,
            ModelInterfaceDao modelInterfaceDao,
            ParameterDao parameterDao,
            FeatureDao featureDao,
            FeatureParameterDao featureParameterDao,
            TimeAxisDao timeAxisDao,
            ObjectMapper objectMapper) {
        this.dynamicProcessModelDao = dynamicProcessModelDao;
        this.modelInterfaceDao = modelInterfaceDao;
        this.parameterDao = parameterDao;
        this.featureDao = featureDao;
        this.featureParameterDao = featureParameterDao;
        this.timeAxisDao = timeAxisDao;
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // 接口 1: 基础条件查询
    // =========================================================================
    @Override
    public String selectModelHierarchyIds(String modelName, String modelSourcePaper, String modelAuthor) {
        try {
            DynamicProcessModelEntity mQuery = new DynamicProcessModelEntity();
            if (modelName != null && !modelName.trim().isEmpty()) mQuery.setModelName(modelName);
            if (modelSourcePaper != null && !modelSourcePaper.trim().isEmpty()) mQuery.setModelSourcePaper(modelSourcePaper);
            if (modelAuthor != null && !modelAuthor.trim().isEmpty()) mQuery.setModelAuthor(modelAuthor);

            List<DynamicProcessModelEntity> models = dynamicProcessModelDao.selectByCondition(mQuery);

            // 调用公共辅助方法生成 JSON
            return buildModelHierarchyJson(models);
        } catch (Exception e) {
            throw new RuntimeException("查询模型层级失败", e);
        }
    }

    // =========================================================================
    // 接口 2: 特征名称 + IO类型 反查
    // =========================================================================
    @Override
    public String selectModelsByFeatureNameAndIoType(String featureName, String ioType) {
        try {
            // 1. 查找 Feature
            FeatureEntity featureQuery = new FeatureEntity();
            featureQuery.setFeatureName(featureName);
            List<FeatureEntity> features = featureDao.selectByCondition(featureQuery);

            Set<Integer> modelIdSet = new HashSet<>();
            for (FeatureEntity feat : features) {
                FeatureParameterEntity fpQuery = new FeatureParameterEntity();
                fpQuery.setFeatureId(feat.getId());
                List<FeatureParameterEntity> fpList = featureParameterDao.selectByDynamicAttributes(fpQuery);

                for (FeatureParameterEntity fp : fpList) {
                    ParameterEntity pQuery = new ParameterEntity();
                    pQuery.setId(fp.getParameterId());
                    if (ioType != null && !ioType.isEmpty()) {
                        pQuery.setIoType(ioType);
                    }
                    List<ParameterEntity> params = parameterDao.selectByCondition(pQuery);
                    for (ParameterEntity param : params) {
                        ModelInterfaceEntity iface = modelInterfaceDao.selectById(param.getInterfaceId());
                        if (iface != null && iface.getProcessmodelId() != null) {
                            modelIdSet.add(iface.getProcessmodelId());
                        }
                    }
                }
            }

            // 2. 根据 ID 集合获取完整的 Model 对象列表
            List<DynamicProcessModelEntity> models = new ArrayList<>();
            for (Integer id : modelIdSet) {
                DynamicProcessModelEntity model = dynamicProcessModelDao.selectById(id);
                if (model != null) {
                    models.add(model);
                }
            }

            // 3. 生成 JSON
            return buildModelHierarchyJson(models);

        } catch (Exception e) {
            throw new RuntimeException("根据特征反查模型详情失败", e);
        }
    }

    // =========================================================================
    // 接口 3: 时空条件反查
    // =========================================================================
    @Override
    public String selectModelsByOutputParameterConditions(
            String ioType,
            Integer temporalResolutionValue,
            String temporalResolutionUnit,
            Integer temporalCount,
            String wktPolygon
    ) {
        try {
            // 步骤 1: 时间维度筛选
            Set<Integer> validTimeParamIds = null;
            boolean hasTimeCondition = (temporalResolutionValue != null ||
                    (temporalResolutionUnit != null && !temporalResolutionUnit.isEmpty()) ||
                    temporalCount != null);

            if (hasTimeCondition) {
                TimeAxisEntity timeQuery = new TimeAxisEntity();
                if (temporalResolutionValue != null) timeQuery.setResolution(Double.valueOf(temporalResolutionValue));
                if (temporalResolutionUnit != null) timeQuery.setUnit(temporalResolutionUnit);
                if (temporalCount != null) timeQuery.setCount(temporalCount);

                List<TimeAxisEntity> timeAxes = timeAxisDao.selectByCondition(timeQuery);
                validTimeParamIds = timeAxes.stream()
                        .map(TimeAxisEntity::getParameterId)
                        .collect(Collectors.toSet());

                if (validTimeParamIds.isEmpty()) {
                    return "[]"; // 没查到时间匹配的，直接返回空数组
                }
            }

            // 步骤 2: 空间与属性维度筛选
            ParameterEntity paramQuery = new ParameterEntity();
            if (ioType != null && !ioType.isEmpty()) {
                paramQuery.setIoType(ioType);
            }

            if (wktPolygon != null && !wktPolygon.isEmpty()) {
                org.locationtech.jts.io.WKTReader reader = new org.locationtech.jts.io.WKTReader();
                org.locationtech.jts.geom.Geometry geom = reader.read(wktPolygon);
                geom.setSRID(4326);
                paramQuery.setCoverageGeom(geom);
            }

            List<ParameterEntity> parameters = parameterDao.selectByCondition(paramQuery);

            // 步骤 3: 取交集并收集 Model ID
            Set<Integer> modelIds = new HashSet<>();
            for (ParameterEntity param : parameters) {
                if (hasTimeCondition && !validTimeParamIds.contains(param.getId())) {
                    continue;
                }
                ModelInterfaceEntity iface = modelInterfaceDao.selectById(param.getInterfaceId());
                if (iface != null && iface.getProcessmodelId() != null) {
                    modelIds.add(iface.getProcessmodelId());
                }
            }

            // 步骤 4: 根据 ID 集合获取完整的 Model 对象列表
            List<DynamicProcessModelEntity> models = new ArrayList<>();
            for (Integer id : modelIds) {
                DynamicProcessModelEntity model = dynamicProcessModelDao.selectById(id);
                if (model != null) {
                    models.add(model);
                }
            }

            // 步骤 5: 生成 JSON
            return buildModelHierarchyJson(models);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("根据时空条件反查模型详情失败: " + e.getMessage());
        }
    }

    // =========================================================================
    // 🔥 核心私有方法：构建完整的模型层级结构 JSON
    // =========================================================================
    private String buildModelHierarchyJson(List<DynamicProcessModelEntity> models) throws Exception {
        if (models == null || models.isEmpty()) {
            return "[]";
        }

        List<Map<String, Object>> resultList = new ArrayList<>();

        for (DynamicProcessModelEntity model : models) {
            Map<String, Object> modelMap = new HashMap<>();
            modelMap.put("processmodelId", model.getId());
            modelMap.put("modelName", model.getModelName());
            modelMap.put("modelAuthor", model.getModelAuthor());
            modelMap.put("modelSourcePaper", model.getModelSourcePaper());
            modelMap.put("version", model.getVersion());
            modelMap.put("modelSummary", model.getModelSummary());

            // 查接口
            ModelInterfaceEntity iQuery = new ModelInterfaceEntity();
            iQuery.setProcessmodelId(model.getId());
            List<ModelInterfaceEntity> interfaces = modelInterfaceDao.selectByCondition(iQuery);

            List<Map<String, Object>> interfaceMaps = new ArrayList<>();
            for (ModelInterfaceEntity iface : interfaces) {
                Map<String, Object> ifaceMap = new HashMap<>();
                ifaceMap.put("interfaceId", iface.getId());
                ifaceMap.put("interfaceName", iface.getInterfaceName());

                // 查参数
                ParameterEntity pQuery = new ParameterEntity();
                pQuery.setInterfaceId(iface.getId());
                List<ParameterEntity> parameters = parameterDao.selectByCondition(pQuery);

                List<Map<String, Object>> parameterMaps = new ArrayList<>();
                for (ParameterEntity param : parameters) {
                    Map<String, Object> paramMap = new HashMap<>();
                    paramMap.put("parameterId", param.getId());
                    paramMap.put("ioType", param.getIoType());

                    // 自动转 GeoJSON (由 Jackson JtsModule 支持)
                    paramMap.put("originPoint", param.getOriginPoint());
                    paramMap.put("coverageGeom", param.getCoverageGeom());

                    // 查 Feature ID
                    FeatureParameterEntity fpQuery = new FeatureParameterEntity();
                    fpQuery.setParameterId(param.getId());
                    List<FeatureParameterEntity> fpList = featureParameterDao.selectByDynamicAttributes(fpQuery);

                    List<Integer> featureIds = fpList.stream()
                            .map(FeatureParameterEntity::getFeatureId)
                            .collect(Collectors.toList());

                    paramMap.put("featureIds", featureIds);
                    parameterMaps.add(paramMap);
                }
                ifaceMap.put("parameters", parameterMaps);
                interfaceMaps.add(ifaceMap);
            }
            modelMap.put("interfaces", interfaceMaps);
            resultList.add(modelMap);
        }

        return objectMapper.writeValueAsString(resultList);
    }
}