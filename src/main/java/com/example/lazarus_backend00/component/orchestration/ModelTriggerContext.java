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
 * 模型触发状态上下文 (原生严格还原版)
 */
public class ModelTriggerContext {

    private final int modelId;
    private final List<Parameter> inputParameters;
    private final List<Parameter> outputParameters;
    private final Duration timeStep;
    private final Duration inputWindow;
    private final List<Integer> inputFeatureIds;

    private Instant modelLastComputedTime;

    // Key: FeatureID -> Value: (TimeSlot -> State[0=Missing, 1=HasData])
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

    // 🔥 仅新增此方法：供 AOP 审计日志抓取当前的数据更新状态表
    public Map<Integer, Map<Instant, Integer>> getFeatureStateMap() {
        return featureStateMap;
    }

    public int getModelId() { return modelId; }
    public List<Integer> getInputFeatureIds() { return Collections.unmodifiableList(inputFeatureIds); }

    public synchronized List<ExecutableTask> processUpdate(TSShell dataShell, int dataState, Instant globalTNow) {
        int fid = dataShell.getFeatureId();

        Instant earliestChangeTime = updateStateAndGetChangeTime(fid, dataShell, dataState);

        if (earliestChangeTime == null) {
            return Collections.emptyList();
        }

        Instant searchLimit;
        if (earliestChangeTime.isBefore(modelLastComputedTime)) {
            searchLimit = earliestChangeTime.minus(timeStep);
            System.out.println(String.format("⚡ [回溯触发] 模型%d 感知到数据变更 @ %s (Signal=%d). 回溯至: %s",
                    modelId, earliestChangeTime, dataState, searchLimit));
        } else {
            searchLimit = modelLastComputedTime;
        }

        return runUDCR(dataShell, globalTNow, searchLimit);
    }

    private Instant updateStateAndGetChangeTime(int fid, TSShell shell, int signalType) {
        Map<Instant, Integer> timeSlots = featureStateMap.get(fid);
        if (timeSlots == null) return null;

        Instant start = alignTime(shell.getTOrigin(), timeStep);
        Instant end = alignTime(shell.getTEnd(), timeStep);
        Instant earliestChange = null;

        for (Instant t = start; !t.isAfter(end); t = t.plus(timeStep)) {
            Integer oldState = timeSlots.getOrDefault(t, 0);
            boolean effectiveChange = false;

            if (signalType == 1) {
                if (oldState == 0) effectiveChange = true;
            } else if (signalType == 2) {
                effectiveChange = true;
            }

            if (effectiveChange) {
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
                if (slots.getOrDefault(t, 0) == 0) return false;
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

        // 严格遵循你原生的 TaskPort 和 TSShellFactory 组装逻辑
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

        // 严格使用你的原生构造函数
        return new ExecutableTask(taskId, this.modelId, inputPorts, outputPorts);
    }

    private Instant alignTime(Instant time, Duration step) {
        long stepMillis = step.toMillis();
        if (stepMillis == 0) return time;
        long epochMillis = time.toEpochMilli();
        return Instant.ofEpochMilli((epochMillis / stepMillis) * stepMillis);
    }
}