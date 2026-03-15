package com.example.lazarus_backend00.service;

import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.dto.DynamicProcessModelDTO;
import com.example.lazarus_backend00.dto.ModelContainerDTO;
import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;
import com.example.lazarus_backend00.infrastructure.persistence.entity.ModelInterfaceEntity;

import java.util.List;

public interface ContainerPoolService {

    /**
     * 获取所有模型元数据
     */
    List<ModelContainerDTO> getAllModels();

//    /**
//     * 核心：根据模型ID实例化容器
//     * 包括：查重、状态初始化、空间参数解析、特征提取
//     */
//    ModelContainerDTO registerContainer(Integer modelId);
    /**
     * 核心：根据模型ID和指定的接口ID实例化容器
     * 包括：查重、状态初始化、空间参数解析、特征提取
     * @param modelId 模型ID
     * @param interfaceId 参数接口ID (允许为null，为null时使用默认接口)
     */
    ModelContainerDTO registerContainer(Integer modelId, Integer interfaceId);

    /**
     * 获取内存中所有正在运行的容器
     */
    List<ModelContainerDTO> getRunningContainers();
    // 新增方法：获取指定模型的所有接口
    List<ModelInterfaceEntity> getModelInterfaces(Integer modelId);

    List<Parameter> getInterfaceParameters(Integer interfaceId);
}