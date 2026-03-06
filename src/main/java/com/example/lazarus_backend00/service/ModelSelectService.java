package com.example.lazarus_backend00.service;

import java.util.List;

public interface ModelSelectService {

    /**
     * 1. 根据模型名称、论文来源、作者组合查询
     */
    String selectModelHierarchyIds(String modelName, String modelSourcePaper, String modelAuthor);

    /**
     * 2. 按照特征名称和IO类型查找关联的模型 (返回完整JSON)
     *
     * @param featureName 特征名称
     * @param ioType "INPUT" 或 "OUTPUT"，如果传 null 则不限制类型
     * @return 完整模型层级信息的 JSON 字符串
     */
    String selectModelsByFeatureNameAndIoType(String featureName, String ioType);

    /**
     * 3. 按照参数的时空属性查找关联的模型 (返回完整JSON)
     *
     * @param ioType 输入/输出类型
     * @param temporalResolutionValue 时间分辨率数值
     * @param temporalResolutionUnit 时间分辨率单位
     * @param temporalCount 时间步数
     * @param wktPolygon 空间范围WKT字符串
     * @return 完整模型层级信息的 JSON 字符串
     */
    String selectModelsByOutputParameterConditions(
            String ioType,
            Integer temporalResolutionValue,
            String temporalResolutionUnit,
            Integer temporalCount,
            String wktPolygon
    );
    /**
     * 4. 根据模型ID查询真实模型完整结构 (返回完整JSON)
     *
     * @param processmodelId 模型ID
     * @return 完整模型层级信息的 JSON 字符串
     */
    String selectModelById(Integer processmodelId);
}