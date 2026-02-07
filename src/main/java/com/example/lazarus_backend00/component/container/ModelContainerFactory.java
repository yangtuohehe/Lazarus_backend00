package com.example.lazarus_backend00.component.container;

import com.example.lazarus_backend00.dao.DynamicProcessModelDao;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型容器工厂 (纯组装模式)
 * 职责：
 * 1. 接收业务层传来的 ID、引擎类型、参数列表。
 * 2. 注入基础设施层的 DAO (用于延迟加载)。
 * 3. 生产并返回运行时容器实例。
 */
@Component
public class ModelContainerFactory {

    // ================== 引擎类型常量 ==================
    public static final String ENGINE_ONNX = "ONNX";
    public static final String ENGINE_DJL_TORCH = "DJL_PYTORCH";
    public static final String ENGINE_REMOTE = "REMOTE_HTTP"; // 预留

    // ================== 核心依赖 ==================
    // 注入 DAO，工厂本身不用它查数据，只是把它传递给容器
    private final DynamicProcessModelDao modelDao;

    // ================== 内部注册表 ==================
    // 函数式接口：定义如何构建一个容器
    @FunctionalInterface
    private interface ContainerBuilder {
        ModelContainer build(int id, Integer version, List<Parameter> params, DynamicProcessModelDao dao);
    }

    private final Map<String, ContainerBuilder> registry = new HashMap<>();

    /**
     * 构造函数：Spring 自动注入 DAO
     */
    public ModelContainerFactory(DynamicProcessModelDao modelDao) {
        this.modelDao = modelDao;
        initRegistry();
    }

    /**
     * 初始化支持的容器类型
     */
    private void initRegistry() {
        // 1. 注册 ONNX 容器构建逻辑
        registry.put(ENGINE_ONNX, (id, ver, params, dao) -> new OnnxModelContainer(
                id,
                ver,
                params,
                dao,
                100 * 1024 * 1024 // 预估大小 100MB (可作为参数传入优化)
        ));

        // 2. 注册 PyTorch (DJL) 容器构建逻辑
        registry.put(ENGINE_DJL_TORCH, (id, ver, params, dao) -> new DjlPytorchModelContainer(
                id,
                ver,
                params,
                dao, // 假设 DjlPytorch 也已适配 DAO 延迟加载
                200 * 1024 * 1024
        ));

        // 3. 远程容器暂不需要 DAO，如果以后需要支持，可以在这里扩展
    }

    /**
     * 【核心工厂方法】
     * * @param modelId    模型ID (数据库主键)
     * @param engineType 引擎类型字符串 (必须由 Service 层决策好传进来，如 "ONNX")
     * @param parameters 业务参数列表 (必须由 Service 层从 Entity 转换好传进来)
     * @return 已实例化的容器 (状态为 CREATED，尚未 load)
     */
    public ModelContainer createContainer(int modelId, Integer version, String engineType, List<Parameter> parameters) {
        if (engineType == null || engineType.trim().isEmpty()) {
            throw new IllegalArgumentException("创建容器失败：必须指定 engineType");
        }
        int finalVersion = (version != null) ? version : 1;

        // 1. 统一格式化 Key (转大写)
        String typeKey = engineType.toUpperCase();

        // 2. 查找对应的构建策略
        ContainerBuilder builder = registry.get(typeKey);

        // 3. 容错处理
        if (builder == null) {
            // 严格模式：不支持的类型直接报错
            throw new IllegalArgumentException("工厂不支持该模型引擎类型: " + engineType);

            // 或者：如果你的系统以 ONNX 为主，可以打开下面的回退逻辑
            // builder = registry.get(ENGINE_ONNX);
        }

        // 4. 执行构建
        // 版本号 "v1.0" 目前写死，后续可以将其加入到方法参数中
        return builder.build(modelId, version, parameters, this.modelDao);
    }
}