package com.example.lazarus_backend00.service;

import com.example.lazarus_backend00.dto.DynamicProcessModelDTO;
import com.example.lazarus_backend00.dto.ModelContainerDTO;
import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;

import java.util.List;

public interface ContainerPoolService {

    /**
     * 获取所有模型元数据
     */
    List<ModelContainerDTO> getAllModels();

    /**
     * 核心：根据模型ID实例化容器
     * 包括：查重、状态初始化、空间参数解析、特征提取
     */
    ModelContainerDTO registerContainer(Integer modelId);

    /**
     * 获取内存中所有正在运行的容器
     */
    List<ModelContainerDTO> getRunningContainers();
}