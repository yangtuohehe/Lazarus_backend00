package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.component.container.ContainerStatus;
import com.example.lazarus_backend00.component.container.ModelContainer;
import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.component.orchestration.ModelEventTrigger;
import com.example.lazarus_backend00.component.pool.ModelContainerPool;
import com.example.lazarus_backend00.dao.DynamicProcessModelDao;
import com.example.lazarus_backend00.dao.ModelInterfaceDao;
import com.example.lazarus_backend00.domain.axis.Feature;
import com.example.lazarus_backend00.dto.ModelContainerDTO;
import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;
import com.example.lazarus_backend00.infrastructure.persistence.entity.ModelInterfaceEntity;
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
    private final ModelInterfaceDao interfaceDao;
    // 🎯 注入触发器 (使用 @Lazy 防止与 Orchestrator 循环依赖)
    private final ModelEventTrigger modelEventTrigger;

    private final Map<Integer, ModelContainerDTO> dtoCache = new ConcurrentHashMap<>();
    // 修改前：private final Map<Integer, Integer> dbIdToRuntimeIdMap = new ConcurrentHashMap<>();
    // 修改后：将 key 改为 String，格式为 "modelId:interfaceId" 以支持同模型多接口共存
    private final Map<String, Integer> dbIdToRuntimeIdMap = new ConcurrentHashMap<>();

    private final AtomicInteger idGenerator = new AtomicInteger(1000);

    public ContainerPoolServiceImpl(DynamicProcessModelDao modelDao,
                                    ModelContainerProvider containerProvider,
                                    ModelContainerPool realExecutionPool,
                                    @Lazy ModelEventTrigger modelEventTrigger,
                                    ModelInterfaceDao interfaceDao) {
        this.modelDao = modelDao;
        this.containerProvider = containerProvider;
        this.realExecutionPool = realExecutionPool;
        this.modelEventTrigger = modelEventTrigger;
        this.interfaceDao = interfaceDao;
    }


    @Override
    public List<Parameter> getInterfaceParameters(Integer interfaceId) {
        return containerProvider.getParametersByInterface(interfaceId);
    }


    @Override
    public List<ModelInterfaceEntity> getModelInterfaces(Integer modelId) {
        ModelInterfaceEntity query = new ModelInterfaceEntity();
        query.setProcessmodelId(modelId);
        return interfaceDao.selectByCondition(query);
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

//    @Override
//    public ModelContainerDTO registerContainer(Integer dbModelId) {
//        DynamicProcessModelEntity modelEntity = modelDao.selectById(dbModelId);
//        if (modelEntity == null) {
//            throw new IllegalArgumentException("模型不存在，DB_ID: " + dbModelId);
//        }
//
//        if (dbIdToRuntimeIdMap.containsKey(dbModelId)) {
//            Integer existingRuntimeId = dbIdToRuntimeIdMap.get(dbModelId);
//            System.out.println(">>> [Service] 模型已存在，直接返回副本。RuntimeID: " + existingRuntimeId);
//            return dtoCache.get(existingRuntimeId);
//        }
//
//        ModelContainer container = containerProvider.reconstructContainer(dbModelId);
//        int runtimeId = idGenerator.getAndIncrement();
//
//        realExecutionPool.registerContainer(runtimeId, container);
//
//        if (modelEventTrigger != null) {
//            modelEventTrigger.registerModel(runtimeId, container.getParameterList());
//        }
//
//        ModelContainerDTO dto = convertInstanceToDTO(runtimeId, container, modelEntity);
//        dbIdToRuntimeIdMap.put(dbModelId, runtimeId);
//        dtoCache.put(runtimeId, dto);
//
//        return dto;
//    }

    @Override
    public ModelContainerDTO registerContainer(Integer dbModelId, Integer interfaceId) {
        DynamicProcessModelEntity modelEntity = modelDao.selectById(dbModelId);
        if (modelEntity == null) {
            throw new IllegalArgumentException("模型不存在，DB_ID: " + dbModelId);
        }

        // 生成唯一的缓存 Key。如果 interfaceId 为 null，用 "default" 代替
        String cacheKey = dbModelId + ":" + (interfaceId != null ? interfaceId : "default");

        if (dbIdToRuntimeIdMap.containsKey(cacheKey)) {
            Integer existingRuntimeId = dbIdToRuntimeIdMap.get(cacheKey);
            System.out.println(">>> [Service] 模型已存在，直接返回副本。RuntimeID: " + existingRuntimeId);
            return dtoCache.get(existingRuntimeId);
        }

        // ================= 核心修改点 =================
        // 将 API 传进来的 interfaceId 透传给底层的构建提供者
        ModelContainer container = containerProvider.reconstructContainer(dbModelId, interfaceId);
        // ===========================================

        int runtimeId = idGenerator.getAndIncrement();

        realExecutionPool.registerContainer(runtimeId, container);

        if (modelEventTrigger != null) {
            modelEventTrigger.registerModel(runtimeId, container.getParameterList());
        }

        ModelContainerDTO dto = convertInstanceToDTO(runtimeId, container, modelEntity);

        // 使用新的 cacheKey 存入缓存
        dbIdToRuntimeIdMap.put(cacheKey, runtimeId);
        dtoCache.put(runtimeId, dto);

        return dto;
    }


//    public void refreshContainerModel(Integer dbModelId) {
//        Integer runtimeId = dbIdToRuntimeIdMap.get(dbModelId);
//        if (runtimeId == null) return;
//
//        ModelContainer container = realExecutionPool.getContainer(runtimeId);
//        if (container != null) {
//            synchronized (container) {
//                container.unload();
//                ModelContainerDTO dto = dtoCache.get(runtimeId);
//                if(dto != null) dto.setStatus(ContainerStatus.CREATED);
//            }
//        }
//    }

    public void refreshContainerModel(Integer dbModelId, Integer interfaceId) {
        String cacheKey = dbModelId + ":" + (interfaceId != null ? interfaceId : "default");
        Integer runtimeId = dbIdToRuntimeIdMap.get(cacheKey);
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