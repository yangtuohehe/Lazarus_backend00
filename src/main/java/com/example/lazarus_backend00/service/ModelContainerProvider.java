package com.example.lazarus_backend00.service;

import com.example.lazarus_backend00.component.container.ModelContainer;
import com.example.lazarus_backend00.component.container.Parameter;

import java.util.List;

/**
 * 模型容器构建服务 (原 ModelLoadingService)
 * 职责：作为“组装车间”，负责从数据库读取元数据，构建出容器的 Java 实例。
 * 注意：此服务仅负责“实例化 (Instantiation)”，不负责“加载资源 (Loading)”。
 * 返回的容器处于 CREATED 状态，尚未占用显存。
 */
public interface ModelContainerProvider {

    /**
     * 重建容器实例 (Reconstruct)
     * 语义：从数据库中将沉睡的模型数据“还原”为一个可操作的 Java 对象。
     * * @param modelId 数据库中的 dynamic_process_model.id
     * @return 已实例化但未加载数据的容器 (Lightweight Object)
     */
    ModelContainer reconstructContainer(Integer modelId);
    // 新增 interfaceId 参数，允许为 null
    ModelContainer reconstructContainer(Integer modelId, Integer interfaceId);
    List<Parameter> getParametersByInterface(Integer interfaceId);
}