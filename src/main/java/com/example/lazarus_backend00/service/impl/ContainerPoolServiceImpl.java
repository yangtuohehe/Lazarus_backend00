package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.component.container.ContainerStatus;
import com.example.lazarus_backend00.component.container.ModelContainer;
import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.component.orchestration.ModelEventTrigger;
import com.example.lazarus_backend00.component.pool.ModelContainerPool;
import com.example.lazarus_backend00.dao.DynamicProcessModelDao;
import com.example.lazarus_backend00.domain.axis.Feature;
import com.example.lazarus_backend00.dto.ModelContainerDTO;
import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;
import com.example.lazarus_backend00.service.ContainerPoolService;
import com.example.lazarus_backend00.service.ModelContainerProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ContainerPoolServiceImpl implements ContainerPoolService {

    private final DynamicProcessModelDao modelDao;
    private final ModelContainerProvider containerProvider;
    private final ModelContainerPool realExecutionPool;

    // 🎯 注入触发器 (使用 @Lazy 防止与 Orchestrator 循环依赖)
    private final ModelEventTrigger modelEventTrigger;

    private final Map<Integer, ModelContainerDTO> dtoCache = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> dbIdToRuntimeIdMap = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1000);

    public ContainerPoolServiceImpl(DynamicProcessModelDao modelDao,
                                    ModelContainerProvider containerProvider,
                                    ModelContainerPool realExecutionPool,
                                    @Lazy ModelEventTrigger modelEventTrigger) {
        this.modelDao = modelDao;
        this.containerProvider = containerProvider;
        this.realExecutionPool = realExecutionPool;
        this.modelEventTrigger = modelEventTrigger;
    }

    @Override
    public List<ModelContainerDTO> getAllModels() {
        List<DynamicProcessModelEntity> entities = modelDao.selectByCondition(new DynamicProcessModelEntity());
        List<ModelContainerDTO> libraryList = new ArrayList<>();

        for (DynamicProcessModelEntity entity : entities) {
            try {
                ModelContainer domainContainer = containerProvider.reconstructContainer(entity.getId());
                libraryList.add(convertDefinitionToDTO(domainContainer, entity));
            } catch (Exception e) {
                System.err.println(">>> [Service] 模型库加载警告: ID=" + entity.getId() + " 数据不完整: " + e.getMessage());
            }
        }
        return libraryList;
    }

    @Override
    public List<ModelContainerDTO> getRunningContainers() {
        return new ArrayList<>(dtoCache.values());
    }

    @Override
    public ModelContainerDTO registerContainer(Integer dbModelId) {
        DynamicProcessModelEntity modelEntity = modelDao.selectById(dbModelId);
        if (modelEntity == null) {
            throw new IllegalArgumentException("模型不存在，DB_ID: " + dbModelId);
        }

        if (dbIdToRuntimeIdMap.containsKey(dbModelId)) {
            Integer existingRuntimeId = dbIdToRuntimeIdMap.get(dbModelId);
            System.out.println(">>> [Service] 模型已存在，直接返回副本。RuntimeID: " + existingRuntimeId);
            return dtoCache.get(existingRuntimeId);
        }

        ModelContainer container = containerProvider.reconstructContainer(dbModelId);
        int runtimeId = idGenerator.getAndIncrement();

        realExecutionPool.registerContainer(runtimeId, container);

        // 🎯 核心联动：严格按照 Python 原型逻辑，步长锁定 1 小时，扫描窗口 24 小时
        if (modelEventTrigger != null) {
            modelEventTrigger.registerModel(
                    runtimeId,
                    container.getParameterList(),
                    Duration.ofHours(1),
                    Duration.ofHours(24)
            );
        }

        ModelContainerDTO dto = convertInstanceToDTO(runtimeId, container, modelEntity);
        dbIdToRuntimeIdMap.put(dbModelId, runtimeId);
        dtoCache.put(runtimeId, dto);

        return dto;
    }

    public void refreshContainerModel(Integer dbModelId) {
        Integer runtimeId = dbIdToRuntimeIdMap.get(dbModelId);
        if (runtimeId == null) return;

        ModelContainer container = realExecutionPool.getContainer(runtimeId);
        if (container != null) {
            synchronized (container) {
                container.unload();
                ModelContainerDTO dto = dtoCache.get(runtimeId);
                if(dto != null) dto.setStatus(ContainerStatus.CREATED);
            }
        }
    }

    private ModelContainerDTO convertDefinitionToDTO(ModelContainer domain, DynamicProcessModelEntity entity) {
        ModelContainerDTO dto = new ModelContainerDTO();
        dto.setId(entity.getId());
        dto.setModelName(entity.getModelName());
        dto.setModelAuthor(entity.getModelAuthor());
        dto.setVersion(entity.getVersion());
        dto.setStatus(ContainerStatus.CREATED);
        dto.setParameters(extractParameters(domain));
        return dto;
    }

    private ModelContainerDTO convertInstanceToDTO(Integer runtimeId, ModelContainer domain, DynamicProcessModelEntity entity) {
        ModelContainerDTO dto = new ModelContainerDTO();
        dto.setId(runtimeId);
        dto.setModelName(entity.getModelName());
        dto.setModelAuthor(entity.getModelAuthor());
        dto.setVersion(entity.getVersion());
        dto.setStatus(ContainerStatus.RUNNING);
        dto.setParameters(extractParameters(domain));
        return dto;
    }

    private List<ModelContainerDTO.ContainerParameter> extractParameters(ModelContainer domain) {
        List<ModelContainerDTO.ContainerParameter> paramDTOs = new ArrayList<>();
        if (domain.getParameterList() != null) {
            for (Parameter p : domain.getParameterList()) {
                List<String> featureNames = new ArrayList<>();
                if (p.getFeatureList() != null) {
                    featureNames = p.getFeatureList().stream()
                            .map(Feature::getFeatureName)
                            .collect(Collectors.toList());
                }
                paramDTOs.add(new ModelContainerDTO.ContainerParameter(p.getIoType(), p.getCoverageGeom(), featureNames));
            }
        }
        return paramDTOs;
    }
}