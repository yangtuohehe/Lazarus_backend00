package com.example.lazarus_backend00.component.orchestration;

import com.example.lazarus_backend00.component.clock.VirtualTimeTickEvent;
import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.domain.data.TSShell;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 全局模型触发管理器 (POJO版, 由 ModelConfig 创建 Bean)
 * 职责：
 * 1. 监听虚拟时间。
 * 2. 管理所有模型的 Context。
 * 3. 路由数据变更事件到对应的模型 Context。
 */
// ❌ 移除 @Component，由 ModelConfig @Bean 统一接管
public class ModelEventTrigger {

    // 注册表：Key=ModelID
    private final Map<Integer, ModelTriggerContext> contextRegistry = new ConcurrentHashMap<>();

    // 倒排索引：FeatureID -> List<Context>
    // 含义：当 FeatureID 更新时，通知哪些 Context
    private final Map<Integer, List<ModelTriggerContext>> featureSubscriberMap = new ConcurrentHashMap<>();

    // 当前虚拟时间 (volatile 保证可见性)
    private volatile Instant tNow;

    /**
     * 🔥 修正：添加带参构造函数，适配 ModelConfig
     */
    public ModelEventTrigger(Instant initialTime) {
        this.tNow = initialTime;
        System.out.println("====== [Trigger] 初始化完成，初始虚拟时间: " + this.tNow + " ======");
    }

    /**
     * 监听虚拟时钟，更新 tNow
     */
    @EventListener
    public void onVirtualTimeTick(VirtualTimeTickEvent event) {
        this.tNow = event.getVirtualTime();
        // System.out.println("🧠 [Trigger] 虚拟时间已同步: " + this.tNow);
    }

    /**
     * 注册模型
     */
    public void registerModel(int modelId, List<Parameter> parameters, Duration timeStep) {
        // 1. 创建 Context (传入当前的 tNow 作为初始水位线)
        ModelTriggerContext context = new ModelTriggerContext(modelId, parameters, timeStep, tNow);

        // 2. 存入主注册表
        contextRegistry.put(modelId, context);

        // 3. 构建倒排索引 (用于消息路由)
        List<Integer> inputFeatureIds = context.getInputFeatureIds();

        for (Integer inputId : inputFeatureIds) {
            // computeIfAbsent 保证线程安全的 List 初始化
            featureSubscriberMap
                    .computeIfAbsent(inputId, k -> new CopyOnWriteArrayList<>())
                    .add(context);
        }

        System.out.println(">>> [Trigger] 模型 " + modelId + " 已注册 (监听 " + inputFeatureIds.size() + " 个特征)");
    }

    /**
     * 注销模型
     */
    public void unregisterModel(int modelId) {
        ModelTriggerContext context = contextRegistry.remove(modelId);
        if (context == null) return;

        // 从倒排索引中移除
        for (Integer featureId : context.getInputFeatureIds()) {
            List<ModelTriggerContext> subscribers = featureSubscriberMap.get(featureId);
            if (subscribers != null) {
                subscribers.remove(context);
            }
        }
        System.out.println(">>> [Trigger] 模型 " + modelId + " 已注销");
    }

    /**
     * 数据更新入口
     */
    public List<ExecutableTask> onDataUpdate(TSShell dataShell) {
        // 防御性检查
        if (dataShell == null || !dataShell.hasTime()) {
            return Collections.emptyList();
        }

        // 查找订阅了该 Feature 的所有模型 Context
        List<ModelTriggerContext> subscribers = featureSubscriberMap.get(dataShell.getFeatureId());

        List<ExecutableTask> allTasks = new ArrayList<>();

        if (subscribers != null) {
            for (ModelTriggerContext context : subscribers) {
                // 传入最新的 tNow 执行检查
                allTasks.addAll(context.processUpdate(dataShell, tNow));
            }
        }
        return allTasks;
    }
}