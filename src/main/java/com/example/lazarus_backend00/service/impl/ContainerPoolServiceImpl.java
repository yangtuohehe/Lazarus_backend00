package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.component.container.ContainerStatus;
import com.example.lazarus_backend00.component.container.ModelContainer;
import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.component.pool.ModelContainerPool;
import com.example.lazarus_backend00.dao.DynamicProcessModelDao;
import com.example.lazarus_backend00.domain.axis.Feature;
import com.example.lazarus_backend00.dto.ModelContainerDTO;
import com.example.lazarus_backend00.infrastructure.persistence.entity.DynamicProcessModelEntity;
import com.example.lazarus_backend00.service.ContainerPoolService;
import com.example.lazarus_backend00.service.ModelContainerProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ContainerPoolServiceImpl implements ContainerPoolService {

    // ================== 依赖组件 ==================
    private final DynamicProcessModelDao modelDao;
    private final ModelContainerProvider containerProvider;
    private final ModelContainerPool realExecutionPool; // 真实的执行池

    // ================== 状态管理 ==================

    // 1. DTO 缓存：用于前端快速展示 [RuntimeID -> DTO]
    // Key: 1000, 1001...
    private final Map<Integer, ModelContainerDTO> dtoCache = new ConcurrentHashMap<>();

    // 2. 🔥 核心映射表：[数据库ID -> 运行时ID]
    // Key: 80 (DB) -> Value: 1000 (Runtime)
    // 作用：防止重复注册，以及支持通过 DB_ID 找到实例进行热更新
    private final Map<Integer, Integer> dbIdToRuntimeIdMap = new ConcurrentHashMap<>();

    // 3. ID 生成器 (从 1000 开始，与数据库 ID 区分开)
    private final AtomicInteger idGenerator = new AtomicInteger(1000);

    // ================== 构造器 ==================
    public ContainerPoolServiceImpl(DynamicProcessModelDao modelDao,
                                    ModelContainerProvider containerProvider,
                                    ModelContainerPool realExecutionPool) {
        this.modelDao = modelDao;
        this.containerProvider = containerProvider;
        this.realExecutionPool = realExecutionPool;
    }

    // ================== 1. 获取模型库列表 (左侧列表) ==================
    @Override
    public List<ModelContainerDTO> getAllModels() {
        // 查出数据库所有模型定义
        List<DynamicProcessModelEntity> entities = modelDao.selectByCondition(new DynamicProcessModelEntity());
        List<ModelContainerDTO> libraryList = new ArrayList<>();

        for (DynamicProcessModelEntity entity : entities) {
            try {
                // 借用 Provider 组装 Domain 对象，为了提取完整的 features 和 ioType
                // 注意：这里只是为了读元数据，不需要注册进池子
                ModelContainer domainContainer = containerProvider.reconstructContainer(entity.getId());

                // 转为 DTO (使用 数据库 ID)
                libraryList.add(convertDefinitionToDTO(domainContainer, entity));

            } catch (Exception e) {
                // 容错：防止某个模型元数据损坏导致整个列表加载失败
                System.err.println(">>> [Service] 模型库加载警告: ID=" + entity.getId() + " 数据不完整");
            }
        }
        return libraryList;
    }

    // ================== 2. 获取运行容器列表 (右侧列表) ==================
    @Override
    public List<ModelContainerDTO> getRunningContainers() {
        // 直接返回缓存中的 DTO (它们包含 RuntimeID 和 Geometry)
        return new ArrayList<>(dtoCache.values());
    }

    // ================== 3. 注册容器 (核心逻辑) ==================
    @Override
    public ModelContainerDTO registerContainer(Integer dbModelId) {
        // A. 查元数据
        DynamicProcessModelEntity modelEntity = modelDao.selectById(dbModelId);
        if (modelEntity == null) {
            throw new IllegalArgumentException("模型不存在，DB_ID: " + dbModelId);
        }

        // B. 🔥 查重逻辑：检查映射表
        if (dbIdToRuntimeIdMap.containsKey(dbModelId)) {
            Integer existingRuntimeId = dbIdToRuntimeIdMap.get(dbModelId);
            System.out.println(">>> [Service] 模型已存在，直接返回副本。RuntimeID: " + existingRuntimeId);
            return dtoCache.get(existingRuntimeId);
        }

        // C. 实例化 Domain 对象
        // 注意：container.getContainerId() 此时是 80 (数据库ID)
        ModelContainer container = containerProvider.reconstructContainer(dbModelId);

        // D. 生成运行时 ID
        int runtimeId = idGenerator.getAndIncrement(); // e.g., 1000

        // E. 🔥 注册进真实执行池
        // 关键点：我们告诉池子用 runtimeId(1000) 来存，但 container 内部 ID 依然是 80
        // 这样 executeModel(1000) -> 找到 container -> container.load() -> selectById(80) -> 成功！
        realExecutionPool.registerContainer(runtimeId, container);

        // F. 转换为 DTO (使用 RuntimeID)
        ModelContainerDTO dto = convertInstanceToDTO(runtimeId, container, modelEntity);

        // G. 更新状态表
        dbIdToRuntimeIdMap.put(dbModelId, runtimeId);
        dtoCache.put(runtimeId, dto);

        return dto;
    }

    // ================== 4. 🔥 热更新接口 ==================
    /**
     * 当数据库模型文件更新后，调用此方法刷新容器
     */
    public void refreshContainerModel(Integer dbModelId) {
        // 1. 查找对应的运行时实例
        Integer runtimeId = dbIdToRuntimeIdMap.get(dbModelId);

        if (runtimeId == null) {
            System.out.println(">>> [热更新] 模型 ID=" + dbModelId + " 未在运行，跳过。");
            return;
        }

        // 2. 从池中获取实例
        ModelContainer container = realExecutionPool.getContainer(runtimeId);
        if (container != null) {
            synchronized (container) {
                // 3. 卸载 (释放内存)
                // 状态变为 UNLOADED/CREATED
                container.unload();

                // 4. 更新 DTO 缓存的状态 (可选)
                ModelContainerDTO dto = dtoCache.get(runtimeId);
                if(dto != null) dto.setStatus(ContainerStatus.CREATED);

                System.out.println(">>> [热更新] 容器 (Runtime=" + runtimeId + ", DB=" + dbModelId + ") 已重置。下次调用将自动拉取新文件。");
            }
        }
    }

    // ================== 私有转换方法 ==================

    /**
     * 转换定义态 DTO (左侧列表)
     * ID = Database ID
     */
    private ModelContainerDTO convertDefinitionToDTO(ModelContainer domain, DynamicProcessModelEntity entity) {
        ModelContainerDTO dto = new ModelContainerDTO();
        dto.setId(entity.getId()); // 使用 DB ID
        dto.setModelName(entity.getModelName());
        dto.setModelAuthor(entity.getModelAuthor());
        dto.setVersion(entity.getVersion());
        dto.setStatus(ContainerStatus.CREATED);

        // 提取参数 (特征名 + IO类型)
        dto.setParameters(extractParameters(domain));
        return dto;
    }

    /**
     * 转换运行态 DTO (右侧列表)
     * ID = Runtime ID
     */
    private ModelContainerDTO convertInstanceToDTO(Integer runtimeId, ModelContainer domain, DynamicProcessModelEntity entity) {
        ModelContainerDTO dto = new ModelContainerDTO();
        dto.setId(runtimeId); // 🔥 使用传入的 Runtime ID
        dto.setModelName(entity.getModelName());
        dto.setModelAuthor(entity.getModelAuthor());
        dto.setVersion(entity.getVersion());
        dto.setStatus(ContainerStatus.RUNNING);

        // 提取参数 (特征名 + IO类型 + GeoJSON)
        dto.setParameters(extractParameters(domain));
        return dto;
    }

    /**
     * 通用参数提取逻辑
     */
    private List<ModelContainerDTO.ContainerParameter> extractParameters(ModelContainer domain) {
        List<ModelContainerDTO.ContainerParameter> paramDTOs = new ArrayList<>();

        if (domain.getParameterList() != null) {
            for (Parameter p : domain.getParameterList()) {
                // 提取特征名列表
                List<String> featureNames = new ArrayList<>();
                if (p.getFeatureList() != null) {
                    featureNames = p.getFeatureList().stream()
                            .map(Feature::getFeatureName)
                            .collect(Collectors.toList());
                }

                // 构建参数 DTO
                ModelContainerDTO.ContainerParameter pDto = new ModelContainerDTO.ContainerParameter(
                        p.getIoType(), // Enum -> String
                        p.getCoverageGeom(),  // Geometry
                        featureNames
                );
                paramDTOs.add(pDto);
            }
        }
        return paramDTOs;
    }
}