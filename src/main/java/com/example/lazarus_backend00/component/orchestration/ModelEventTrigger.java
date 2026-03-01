package com.example.lazarus_backend00.component.orchestration;

import com.example.lazarus_backend00.component.clock.VirtualTimeTickEvent;
import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.dto.subdto.DataUpdatePacket;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ModelEventTrigger {

    private final Map<Integer, ModelTriggerContext> contextRegistry = new ConcurrentHashMap<>();
    private final Map<Integer, List<ModelTriggerContext>> featureSubscriberMap = new ConcurrentHashMap<>();
    private volatile Instant tNow;

    public ModelEventTrigger() {
        this.tNow = Instant.parse("2022-01-01T00:00:00Z");
        System.out.println("====== [Trigger] 初始化完成，初始虚拟时间锁定为: " + this.tNow + " ======");
    }

    public Map<String, Object> getTriggerState() {
        Map<String, Object> state = new HashMap<>();
        contextRegistry.forEach((id, context) -> state.put("Model_" + id, context.getFeatureStateMap()));
        return state;
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
        System.out.println(String.format(">>> [Trigger] 模型 %d 已挂载，监听特征列表: %s", modelId, inputFeatureIds));
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
     * 核心接口 1：接收外部数据子系统发来的批量 JSON 广播 (已彻底移除 AOP 拦截注解)
     */
    public List<ExecutableTask> onDataUpdateBatch(List<DataUpdatePacket> packets) {
        List<ExecutableTask> allTasks = new ArrayList<>();
        if (packets == null || packets.isEmpty()) return allTasks;

        System.out.println("\n=========================================================");
        System.out.println("📥 [模型编排系统] 接收到 " + packets.size() + " 条数据更新广播，开始分发...");

        for (DataUpdatePacket packet : packets) {
            if (packet.getShell() != null) {
                allTasks.addAll(onDataUpdate(packet.getShell(), packet.getStatus()));
            }
        }

        // 打印出当前所有模型的状态快照
        printTriggerStateSnapshot();

        // 打印本次新编排好的任务列表
        printOrchestratedTasks(allTasks);

        System.out.println("=========================================================\n");

        return allTasks;
    }

    public List<ExecutableTask> onModelCalculated(TSShell calculatedShell) {
        System.out.println("🔄 [图谱回流] 内部模型产生新数据 (Feature=" + calculatedShell.getFeatureId() + ")，正在唤醒下游...");
        return onDataUpdate(calculatedShell, 1);
    }

    public List<ExecutableTask> onDataUpdate(TSShell dataShell, int dataState) {
        if (dataShell == null || !dataShell.hasTime()) {
            return Collections.emptyList();
        }

        List<ModelTriggerContext> subscribers = featureSubscriberMap.get(dataShell.getFeatureId());
        List<ExecutableTask> allTasks = new ArrayList<>();

        if (subscribers != null) {
            for (ModelTriggerContext context : subscribers) {
                List<ExecutableTask> generatedTasks = context.processUpdate(dataShell, dataState, tNow);
                if (!generatedTasks.isEmpty()) {
                    allTasks.addAll(generatedTasks);
                }
            }
        }
        return allTasks;
    }

    // ================== 🔥 打印辅助方法 ==================

    private void printTriggerStateSnapshot() {
        System.out.println("\n🔍 ====== [状态快照] 当前各模型触发器的数据列表状态 ======");
        if (contextRegistry.isEmpty()) {
            System.out.println("   (当前系统未注册任何模型)");
        }

        contextRegistry.forEach((modelId, context) -> {
            System.out.println("   🧩 [模型 ID: " + modelId + "]");
            Map<Integer, Map<Instant, Integer>> stateMap = context.getFeatureStateMap();

            if (stateMap == null || stateMap.isEmpty()) {
                System.out.println("      (无监听特征)");
            } else {
                stateMap.forEach((featureId, timeSlots) -> {
                    System.out.print("      └─ 特征 [" + featureId + "] -> 收集时刻: [ ");
                    new TreeMap<>(timeSlots).forEach((time, status) -> {
                        if (status == 1) {
                            System.out.print(time + " ✔️ | ");
                        }
                    });
                    System.out.println("]");
                });
            }
        });
    }

    private void printOrchestratedTasks(List<ExecutableTask> tasks) {
        System.out.println("\n🚀 ====== [编排结果] 本次生成的待执行任务列表 ======");
        if (tasks.isEmpty()) {
            System.out.println("   (无新任务生成，依赖特征尚未全部凑齐，继续等待...)");
        } else {
            for (ExecutableTask task : tasks) {
                Instant targetTime = null;
                if (task.getInputs() != null && !task.getInputs().isEmpty()) {
                    ExecutableTask.TaskPort firstPort = task.getInputs().get(0);
                    if (firstPort.getAtomicShells() != null && !firstPort.getAtomicShells().isEmpty()) {
                        targetTime = firstPort.getAtomicShells().get(0).getTOrigin();
                    }
                }
                System.out.println("   ▶️ 任务ID: " + task.getTaskId() + " | 模型ID: " + task.getContainerId() + " | 计算时刻: " + targetTime);
            }
        }
    }
}