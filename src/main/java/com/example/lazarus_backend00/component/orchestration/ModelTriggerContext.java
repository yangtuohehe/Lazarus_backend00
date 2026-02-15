package com.example.lazarus_backend00.component.orchestration;

import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.domain.axis.Feature;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.domain.data.TSShellFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 模型触发状态上下文 (ModelTriggerContext)
 *
 * <p>状态定义 (Update Semantics):
 * <ul>
 * <li>0: 空缺 (Missing) - 无数据。</li>
 * <li>1: 存在 (Has Data) - 内部存储状态，表示该时刻数据就绪 (无论是实测还是模拟)。</li>
 * </ul>
 *
 * <p>输入信号定义 (Input Signal):
 * <ul>
 * <li>Signal 1 (New): 仅当当前为 0 时触发更新。</li>
 * <li>Signal 2 (Replace): 强制触发更新 (触发 UDCR)。</li>
 * </ul>
 */
class ModelTriggerContext {

    // ================== 元数据 ==================
    private final int modelId;
    private final List<Parameter> inputParameters;
    private final List<Parameter> outputParameters;
    private final Duration timeStep;
    private final Duration inputWindow;
    private final List<Integer> inputFeatureIds;

    // ================== 动态状态 ==================
    private Instant modelLastComputedTime;

    // Key: FeatureID -> Value: (TimeSlot -> State[0=Missing, 1=HasData])
    // 注意：内部 Map 不再区分实测/模拟，只存 1 表示有数据。区分逻辑在 update 时处理。
    private final Map<Integer, Map<Instant, Integer>> featureStateMap = new ConcurrentHashMap<>();

    private static final AtomicLong taskIdGenerator = new AtomicLong(0);

    public ModelTriggerContext(int modelId, List<Parameter> allParameters, Duration timeStep, Duration inputWindow, Instant initialTime) {
        this.modelId = modelId;
        this.timeStep = timeStep;
        this.inputWindow = (inputWindow != null) ? inputWindow : timeStep;
        this.modelLastComputedTime = initialTime.minus(timeStep);

        this.inputParameters = new ArrayList<>();
        this.outputParameters = new ArrayList<>();
        this.inputFeatureIds = new ArrayList<>();

        for (Parameter p : allParameters) {
            if ("INPUT".equalsIgnoreCase(p.getIoType())) {
                this.inputParameters.add(p);
                if (p.getFeatureList() != null) {
                    for (Feature f : p.getFeatureList()) {
                        this.inputFeatureIds.add(f.getId());
                        this.featureStateMap.put(f.getId(), new ConcurrentHashMap<>());
                    }
                }
            } else if ("OUTPUT".equalsIgnoreCase(p.getIoType())) {
                this.outputParameters.add(p);
            }
        }
    }

    public int getModelId() { return modelId; }
    public List<Integer> getInputFeatureIds() { return Collections.unmodifiableList(inputFeatureIds); }

    /**
     * 处理数据更新信号
     * @param dataState 1:新增(New), 2:替换(Replace)
     */
    public synchronized List<ExecutableTask> processUpdate(TSShell dataShell, int dataState, Instant globalTNow) {
        int fid = dataShell.getFeatureId();

        // 1. 根据信号类型 (New vs Replace) 更新状态机
        // 返回最早发生"有效变更"的时间点
        Instant earliestChangeTime = updateStateAndGetChangeTime(fid, dataShell, dataState);

        if (earliestChangeTime == null) {
            return Collections.emptyList(); // 无有效变更 (例如信号是1但已有数据)
        }

        // 2. 确定搜索截止时间 (Search Limit)
        Instant searchLimit;
        if (earliestChangeTime.isBefore(modelLastComputedTime)) {
            // 发生了历史数据的填补(1) 或 修正(2)，需要回溯
            searchLimit = earliestChangeTime.minus(timeStep);
            System.out.println(String.format("⚡ [回溯触发] 模型%d 感知到数据变更 @ %s (Signal=%d). 回溯至: %s",
                    modelId, earliestChangeTime, dataState, searchLimit));
        } else {
            searchLimit = modelLastComputedTime;
        }

        // 3. 执行 UDCR
        return runUDCR(dataShell, globalTNow, searchLimit);
    }

    /**
     * 🔥 核心逻辑变更：状态更新状态机
     *
     * @param signalType 1:新增, 2:替换
     * @return 最早发生有效变更的时间，如果无变更返回 null
     */
    private Instant updateStateAndGetChangeTime(int fid, TSShell shell, int signalType) {
        Map<Instant, Integer> timeSlots = featureStateMap.get(fid);
        if (timeSlots == null) return null;

        Instant start = alignTime(shell.getTOrigin(), timeStep);
        Instant end = alignTime(shell.getTEnd(), timeStep);

        Instant earliestChange = null;

        for (Instant t = start; !t.isAfter(end); t = t.plus(timeStep)) {
            Integer oldState = timeSlots.getOrDefault(t, 0); // 0:空缺, 1:有数据

            boolean effectiveChange = false;

            // === 逻辑分支 ===

            // Scenario A: 信号为 1 (新增)
            // 只有当坑是空的 (old=0) 时，才视为变更。
            // 如果 old=1，说明已有数据 (不管之前是模拟还是实测)，忽略此信号。
            if (signalType == 1) {
                if (oldState == 0) {
                    effectiveChange = true;
                }
            }

            // Scenario B: 信号为 2 (替换/修正)
            // 无论坑里有没有水，都强制视为变更。
            // 目的：强制返回 earliestChangeTime，从而触发 UDCR 重算。
            else if (signalType == 2) {
                effectiveChange = true;
            }

            // === 执行更新 ===
            if (effectiveChange) {
                // 内部状态统一存为 1 (表示数据就绪)，不区分实测/模拟
                timeSlots.put(t, 1);

                if (earliestChange == null || t.isBefore(earliestChange)) {
                    earliestChange = t;
                }
            }
        }
        return earliestChange;
    }

    private List<ExecutableTask> runUDCR(TSShell triggerShell, Instant tNow, Instant searchLimit) {
        List<Instant> validTimePoints = new ArrayList<>();
        Instant p = alignTime(tNow, timeStep);

        while (p.isAfter(searchLimit)) {
            // 完整性检查：只要状态为 1 (有数据) 即可，不关心来源
            if (!checkInputIntegrity(p)) {
                p = p.minus(timeStep);
                continue;
            }

            if (checkDependencyNecessity(p, triggerShell)) {
                validTimePoints.add(p);
            }
            p = p.minus(timeStep);
        }

        Collections.sort(validTimePoints);
        List<ExecutableTask> tasks = new ArrayList<>();
        for (Instant executeTime : validTimePoints) {
            tasks.add(createTask(executeTime));
            if (executeTime.isAfter(modelLastComputedTime)) {
                modelLastComputedTime = executeTime;
            }
        }
        return tasks;
    }

    private boolean checkInputIntegrity(Instant p) {
        Instant windowStart = p.minus(inputWindow);
        Instant windowEnd = p;

        for (Integer inputId : inputFeatureIds) {
            Map<Instant, Integer> slots = featureStateMap.get(inputId);
            for (Instant t = windowStart; !t.isAfter(windowEnd); t = t.plus(timeStep)) {
                // 只要状态 > 0 即视为数据就绪
                if (slots.getOrDefault(t, 0) == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean checkDependencyNecessity(Instant p, TSShell triggerShell) {
        Instant taskInputStart = p.minus(inputWindow);
        Instant taskInputEnd = p.plus(timeStep);
        Instant dataStart = triggerShell.getTOrigin();
        Instant dataEnd = triggerShell.getTEnd();
        Instant intersectStart = taskInputStart.isAfter(dataStart) ? taskInputStart : dataStart;
        Instant intersectEnd = taskInputEnd.isBefore(dataEnd) ? taskInputEnd : dataEnd;
        return intersectStart.isBefore(intersectEnd);
    }

    private ExecutableTask createTask(Instant executeTime) {
        long taskId = taskIdGenerator.incrementAndGet();
        List<ExecutableTask.TaskPort> inputPorts = new ArrayList<>();
        for (Parameter param : inputParameters) {
            List<TSShell> shellsForThisPort = new ArrayList<>();
            if (param.getFeatureList() != null) {
                for (Feature f : param.getFeatureList()) {
                    TSShell shell = TSShellFactory.createFromParameter(f.getId(), executeTime, param);
                    shellsForThisPort.add(shell);
                }
            }
            inputPorts.add(new ExecutableTask.TaskPort(param.getTensorOrder(), shellsForThisPort));
        }
        List<ExecutableTask.TaskPort> outputPorts = new ArrayList<>();
        for (Parameter param : outputParameters) {
            List<TSShell> shellsForThisPort = new ArrayList<>();
            if (param.getFeatureList() != null) {
                for (Feature f : param.getFeatureList()) {
                    TSShell shell = TSShellFactory.createFromParameter(f.getId(), executeTime, param);
                    shellsForThisPort.add(shell);
                }
            }
            outputPorts.add(new ExecutableTask.TaskPort(param.getTensorOrder(), shellsForThisPort));
        }
        return new ExecutableTask(taskId, this.modelId, inputPorts, outputPorts);
    }

    private Instant alignTime(Instant time, Duration step) {
        long stepMillis = step.toMillis();
        if (stepMillis == 0) return time;
        long epochMillis = time.toEpochMilli();
        return Instant.ofEpochMilli((epochMillis / stepMillis) * stepMillis);
    }
}