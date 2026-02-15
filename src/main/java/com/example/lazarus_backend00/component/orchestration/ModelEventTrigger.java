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
 * 全局模型触发管理器
 * 职责：
 * 1. 监听虚拟时间。
 * 2. 管理所有模型的 Context。
 * 3. 路由数据变更事件到对应的模型 Context。
 */
public class ModelEventTrigger {

    private final Map<Integer, ModelTriggerContext> contextRegistry = new ConcurrentHashMap<>();
    private final Map<Integer, List<ModelTriggerContext>> featureSubscriberMap = new ConcurrentHashMap<>();
    private volatile Instant tNow;

    public ModelEventTrigger(Instant initialTime) {
        this.tNow = initialTime;
        System.out.println("====== [Trigger] 初始化完成，初始虚拟时间: " + this.tNow + " ======");
    }

    @EventListener
    public void onVirtualTimeTick(VirtualTimeTickEvent event) {
        this.tNow = event.getVirtualTime();
    }

    public void registerModel(int modelId, List<Parameter> parameters, Duration timeStep, Duration inputWindow) {
        ModelTriggerContext context = new ModelTriggerContext(modelId, parameters, timeStep, inputWindow, tNow);
        contextRegistry.put(modelId, context);

        List<Integer> inputFeatureIds = context.getInputFeatureIds();
        for (Integer inputId : inputFeatureIds) {
            featureSubscriberMap
                    .computeIfAbsent(inputId, k -> new CopyOnWriteArrayList<>())
                    .add(context);
        }
        System.out.println(String.format(">>> [Trigger] 模型 %d 已注册 (Step=%s, Window=%s)", modelId, timeStep, inputWindow));
    }

    public void unregisterModel(int modelId) {
        ModelTriggerContext context = contextRegistry.remove(modelId);
        if (context == null) return;
        for (Integer featureId : context.getInputFeatureIds()) {
            List<ModelTriggerContext> subscribers = featureSubscriberMap.get(featureId);
            if (subscribers != null) subscribers.remove(context);
        }
    }

    /**
     * 数据更新入口
     * @param dataShell 数据外壳
     * @param dataState 数据更新类型
     * 0: 空缺 (Missing)
     * 1: 新增 (New Data) - 仅填补空缺，若已有数据则忽略
     * 2: 替换 (Replace/Correct) - 强制更新数据，并触发回溯计算
     */
    public List<ExecutableTask> onDataUpdate(TSShell dataShell, int dataState) {
        if (dataShell == null || !dataShell.hasTime()) {
            return Collections.emptyList();
        }

        List<ModelTriggerContext> subscribers = featureSubscriberMap.get(dataShell.getFeatureId());
        List<ExecutableTask> allTasks = new ArrayList<>();

        if (subscribers != null) {
            for (ModelTriggerContext context : subscribers) {
                // 透传 dataState，具体的新增/替换逻辑由 Context 内部处理
                allTasks.addAll(context.processUpdate(dataShell, dataState, tNow));
            }
        }
        return allTasks;
    }
}