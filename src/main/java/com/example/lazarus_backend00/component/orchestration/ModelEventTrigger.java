package com.example.lazarus_backend00.component.orchestration;

import com.example.lazarus_backend00.component.clock.VirtualTimeTickEvent;
import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.domain.axis.Feature;
import com.example.lazarus_backend00.domain.axis.TimeAxis;
import com.example.lazarus_backend00.domain.data.*;
import com.example.lazarus_backend00.service.ModelOrchestratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class ModelEventTrigger {

    private final ModelOrchestratorService orchestratorService;
    private final AtomicLong taskIdGenerator = new AtomicLong(1000);

    /** 全局数据状态大盘：FeatureID -> (时刻 -> 状态包) */
    private final Map<Integer, Map<Instant, TSState>> globalStateTable = new ConcurrentHashMap<>();

    /** 注册表 */
    private final Map<Integer, ModelTriggerContext> registry = new ConcurrentHashMap<>();

    /** 依赖图1：Feature 被哪些模型作为 INPUT 使用 (用于状态 2 顺藤摸瓜) */
    private final Map<Integer, List<ModelTriggerContext>> featureInputDag = new ConcurrentHashMap<>();
    /** 依赖图2：Feature 由哪些模型作为 OUTPUT 生成 (用于状态 0 逆向回溯) */
    private final Map<Integer, List<ModelTriggerContext>> featureOutputMap = new ConcurrentHashMap<>();

    private Instant currentPhysicalTime;

    public ModelEventTrigger(ModelOrchestratorService orchestratorService, Instant startTime) {
        this.orchestratorService = orchestratorService;
        this.currentPhysicalTime = startTime;
    }

    public void registerModel(int runtimeId, List<Parameter> params, Duration step, Duration window) {
        ModelTriggerContext ctx = new ModelTriggerContext(runtimeId, params, step, window);
        registry.put(runtimeId, ctx);

        // 构建输入与输出关系图
        for (Parameter p : ctx.getInputParams()) {
            for (Feature f : p.getFeatureList()) {
                featureInputDag.computeIfAbsent(f.getId(), k -> new ArrayList<>()).add(ctx);
            }
        }
        for (Parameter p : ctx.getOutputParams()) {
            for (Feature f : p.getFeatureList()) {
                featureOutputMap.computeIfAbsent(f.getId(), k -> new ArrayList<>()).add(ctx);
            }
        }
    }

    @EventListener
    public void onVirtualTimeTick(VirtualTimeTickEvent event) {
        this.currentPhysicalTime = event.getVirtualTime();
        ensureZerosExistInDataBoard(); // 先把理论上应该存在的 0 (WAITING) 填充进大盘，确保能被遍历到
        scanAndDispatch();
    }

    @EventListener
    public void onDataStateUpdate(DataStateUpdateEvent event) {
        for (TSState incoming : event.getTsStates()) {
            globalStateTable.computeIfAbsent(incoming.getFeatureId(), k -> new ConcurrentHashMap<>())
                    .put(incoming.getTOrigin(), new TSState(incoming));
        }
        scanAndDispatch();
    }

    // ===================================================================================
    // 算法核心：一个数据状态一个数据状态地查！
    // ===================================================================================
    private void scanAndDispatch() {
        Set<String> dispatchedTasks = new HashSet<>();

        // 遍历整个大盘中的每一个像素点/时空状态
        for (Map.Entry<Integer, Map<Instant, TSState>> featureEntry : globalStateTable.entrySet()) {
            int featureId = featureEntry.getKey();

            for (Map.Entry<Instant, TSState> timeEntry : featureEntry.getValue().entrySet()) {
                Instant stateTime = timeEntry.getKey();
                TSState state = timeEntry.getValue();

                // ----------------------------------------------------------------
                // 算法分支 1：发现状态为 2 (REPLACED / 替换态) -> 找下游
                // ----------------------------------------------------------------
                if (state.hasReplacedData()) {
                    List<ModelTriggerContext> downstreamModels = featureInputDag.getOrDefault(featureId, Collections.emptyList());

                    for (ModelTriggerContext ctx : downstreamModels) {
                        // 由该点找到它属于哪一个输入壳
                        Instant inputBaseTime = alignTime(stateTime, calculateInputSpan(ctx));
                        Instant outputStartTime = inputBaseTime.plusSeconds(calculateInputSpan(ctx));

                        String taskKey = ctx.getContainerId() + "_" + inputBaseTime.toEpochMilli();
                        if (dispatchedTasks.contains(taskKey)) continue;

                        int inputStatus = checkInputsStatus(ctx, inputBaseTime);
                        if (inputStatus > 0) {
                            // 发现数据齐全，不生成掩膜，把2变1
                            buildAndSubmitTask(ctx, inputBaseTime, outputStartTime, false);
                            changeInputStates2To1(ctx, inputBaseTime);
                            dispatchedTasks.add(taskKey);
                        }
                    }
                }

                // ----------------------------------------------------------------
                // 算法分支 2：发现状态为 0 (WAITING / 包含空洞) -> 找上游
                // ----------------------------------------------------------------
                else if (state.hasHoles()) {
                    List<ModelTriggerContext> upstreamModels = featureOutputMap.getOrDefault(featureId, Collections.emptyList());

                    for (ModelTriggerContext ctx : upstreamModels) {
                        // 由该点找到它属于哪一个输出壳
                        Instant outputStartTime = alignTime(stateTime, calculateOutputSpan(ctx));
                        Instant inputBaseTime = outputStartTime.minusSeconds(calculateInputSpan(ctx));

                        String taskKey = ctx.getContainerId() + "_" + inputBaseTime.toEpochMilli();
                        if (dispatchedTasks.contains(taskKey)) continue;

                        int inputStatus = checkInputsStatus(ctx, inputBaseTime);
                        if (inputStatus == 1) {
                            // 发现数据齐全且全是1，进行掩膜生成
                            buildAndSubmitTask(ctx, inputBaseTime, outputStartTime, true);
                            changeOutputStates0To1(ctx, outputStartTime); // 把输出预留，防重入
                            dispatchedTasks.add(taskKey);
                        } else if (inputStatus == 2) {
                            // 发现数据齐全但是有2，不生成掩膜，把2变为1
                            buildAndSubmitTask(ctx, inputBaseTime, outputStartTime, false);
                            changeOutputStates0To1(ctx, outputStartTime);
                            changeInputStates2To1(ctx, inputBaseTime);
                            dispatchedTasks.add(taskKey);
                        }
                    }
                }
            }
        }
    }

    // ===================================================================================
    // 算法辅助：位图的就地更改 (把 2 变 1，把 0 变 1)
    // ===================================================================================

    private void changeInputStates2To1(ModelTriggerContext ctx, Instant baseTime) {
        for (Parameter p : ctx.getInputParams()) {
            TimeAxis tAxis = p.getTimeAxis();
            if (tAxis == null) continue;
            long resSec = getAxisResolutionInSeconds(tAxis);
            for (Feature f : p.getFeatureList()) {
                for (int i = 0; i < tAxis.getCount(); i++) {
                    Instant reqT = baseTime.plusSeconds(i * resSec);
                    TSState state = globalStateTable.get(f.getId()).get(reqT);
                    if (state != null && state.hasReplacedData()) {
                        // 算法操作：把 2 清除，并把该位置标为 1
                        state.getReplacedMask().clear();
                        state.getReadyMask().set(0, calculateFrameSize(p));
                    }
                }
            }
        }
    }

    private void changeOutputStates0To1(ModelTriggerContext ctx, Instant outputStartTime) {
        for (Parameter p : ctx.getOutputParams()) {
            TimeAxis tAxis = p.getTimeAxis();
            if (tAxis == null) continue;
            long resSec = getAxisResolutionInSeconds(tAxis);
            for (Feature f : p.getFeatureList()) {
                for (int i = 0; i < tAxis.getCount(); i++) {
                    Instant outT = outputStartTime.plusSeconds(i * resSec);
                    TSState state = globalStateTable.get(f.getId()).get(outT);
                    if (state != null) {
                        // 算法操作：把原本是 0 的坑位强行涂满 1 (占位预防任务风暴)
                        state.getReadyMask().set(0, calculateFrameSize(p));
                    }
                }
            }
        }
    }

    // ===================================================================================
    // 验证逻辑
    // 返回：0(不齐), 1(齐且全1), 2(齐且含2)
    // ===================================================================================
    private int checkInputsStatus(ModelTriggerContext ctx, Instant baseTime) {
        boolean hasState2 = false;
        for (Parameter p : ctx.getInputParams()) {
            TimeAxis tAxis = p.getTimeAxis();
            if (tAxis == null) continue;
            long resSec = getAxisResolutionInSeconds(tAxis);
            for (Feature f : p.getFeatureList()) {
                for (int i = 0; i < tAxis.getCount(); i++) {
                    Instant reqT = baseTime.plusSeconds(i * resSec);
                    Map<Instant, TSState> timeMap = globalStateTable.get(f.getId());

                    if (timeMap == null || !timeMap.containsKey(reqT) || timeMap.get(reqT).hasHoles()) {
                        return 0; // 输入有 0，直接打回
                    }
                    if (timeMap.get(reqT).hasReplacedData()) {
                        hasState2 = true; // 发现 2
                    }
                }
            }
        }
        return hasState2 ? 2 : 1;
    }

    private void buildAndSubmitTask(ModelTriggerContext ctx, Instant inputBaseTime, Instant outputStartTime, boolean applyMask) {
        List<ExecutableTask.TaskPort> inputPorts = new ArrayList<>();
        for (Parameter p : ctx.getInputParams()) {
            List<TSShell> shells = new ArrayList<>();
            for (Feature f : p.getFeatureList()) {
                shells.add(TSShellFactory.createFromParameter(f.getId(), inputBaseTime, p));
            }
            inputPorts.add(new ExecutableTask.TaskPort(p.getTensorOrder(), shells, null));
        }

        List<ExecutableTask.TaskPort> outputPorts = new ArrayList<>();
        for (Parameter p : ctx.getOutputParams()) {
            List<TSShell> shells = new ArrayList<>();
            List<List<TSState>> targetStatesPerFeature = new ArrayList<>();
            long resSec = getAxisResolutionInSeconds(p.getTimeAxis());
            int tCount = (p.getTimeAxis() != null && p.getTimeAxis().getCount() != null) ? p.getTimeAxis().getCount() : 1;

            for (Feature f : p.getFeatureList()) {
                shells.add(TSShellFactory.createFromParameter(f.getId(), outputStartTime, p));
                List<TSState> statesForThisFeature = new ArrayList<>();
                for (int t = 0; t < tCount; t++) {
                    Instant currentFrameTime = outputStartTime.plusSeconds(t * resSec);
                    TSState currentState = globalStateTable.get(f.getId()).get(currentFrameTime);
                    // ⚠️ 深拷贝，把真实的 0 送进任务，当做掩膜比对底图
                    statesForThisFeature.add(new TSState(currentState));
                }
                targetStatesPerFeature.add(statesForThisFeature);
            }
            outputPorts.add(new ExecutableTask.TaskPort(p.getTensorOrder(), shells, targetStatesPerFeature));
        }

        long taskId = taskIdGenerator.getAndIncrement();
        orchestratorService.dispatchTask(new ExecutableTask(taskId, ctx.getContainerId(), applyMask, inputPorts, outputPorts));
    }

    // 辅助工具方法
    private Instant alignTime(Instant t, long spanSec) {
        long tSec = t.getEpochSecond();
        return Instant.ofEpochSecond(tSec - (tSec % spanSec));
    }

    private int calculateFrameSize(Parameter p) {
        int w = 1;
        int h = 1;

        if (p.getAxisList() != null) {
            for (com.example.lazarus_backend00.domain.axis.Axis axis : p.getAxisList()) {
                if (axis instanceof com.example.lazarus_backend00.domain.axis.SpaceAxisX && axis.getCount() != null) {
                    w = axis.getCount();
                } else if (axis instanceof com.example.lazarus_backend00.domain.axis.SpaceAxisY && axis.getCount() != null) {
                    h = axis.getCount();
                }
            }
        }
        return w * h;
    }

    private long getAxisResolutionInSeconds(TimeAxis tAxis) {
        if (tAxis == null || tAxis.getResolution() == null) return 0;
        double res = tAxis.getResolution();
        String unit = (tAxis.getUnit() != null) ? tAxis.getUnit().trim().toLowerCase() : "s";
        return unit.startsWith("h") ? (long)(res * 3600) : (unit.startsWith("m") ? (long)(res * 60) : (long)res);
    }
    /**
     * 注销模型功能 (同时安全清理依赖图，防止内存泄漏)
     */
    public void unregisterModel(int runtimeId) {
        ModelTriggerContext ctx = registry.remove(runtimeId);
        if (ctx != null) {
            // 1. 清理输入依赖图 (DAG 1)
            for (Parameter p : ctx.getInputParams()) {
                for (Feature f : p.getFeatureList()) {
                    List<ModelTriggerContext> list = featureInputDag.get(f.getId());
                    if (list != null) {
                        list.removeIf(c -> c.getContainerId() == runtimeId);
                        if (list.isEmpty()) featureInputDag.remove(f.getId());
                    }
                }
            }

            // 2. 清理输出依赖图 (DAG 2)
            for (Parameter p : ctx.getOutputParams()) {
                for (Feature f : p.getFeatureList()) {
                    List<ModelTriggerContext> list = featureOutputMap.get(f.getId());
                    if (list != null) {
                        list.removeIf(c -> c.getContainerId() == runtimeId);
                        if (list.isEmpty()) featureOutputMap.remove(f.getId());
                    }
                }
            }
            log.info("🗑️ [Trigger] Unregistered Model & Cleaned DAGs. RuntimeID: {}", runtimeId);
        } else {
            log.warn("⚠️ [Trigger] Model not found when unregistering. RuntimeID: {}", runtimeId);
        }
    }
    private long calculateInputSpan(ModelTriggerContext ctx) {
        return ctx.getInputParams().stream().mapToLong(p -> p.getTimeAxis() != null ? (long)p.getTimeAxis().getCount() * getAxisResolutionInSeconds(p.getTimeAxis()) : 0).max().orElse(3600);
    }

    private long calculateOutputSpan(ModelTriggerContext ctx) {
        return ctx.getOutputParams().stream().mapToLong(p -> p.getTimeAxis() != null ? (long)p.getTimeAxis().getCount() * getAxisResolutionInSeconds(p.getTimeAxis()) : 0).max().orElse(3600);
    }

    private void ensureZerosExistInDataBoard() {
        for (ModelTriggerContext ctx : registry.values()) {
            long outSpan = calculateOutputSpan(ctx);
            long startSec = currentPhysicalTime.getEpochSecond() - (100 * 3600);
            startSec -= (startSec % outSpan);
            for (Instant t = Instant.ofEpochSecond(startSec); !t.isAfter(currentPhysicalTime); t = t.plusSeconds(outSpan)) {
                for (Parameter p : ctx.getOutputParams()) {
                    long res = getAxisResolutionInSeconds(p.getTimeAxis());
                    for (Feature f : p.getFeatureList()) {
                        for (int i = 0; i < p.getTimeAxis().getCount(); i++) {
                            Instant frameTime = t.plusSeconds(i * res);
                            globalStateTable.computeIfAbsent(f.getId(), k -> new ConcurrentHashMap<>())
                                    .putIfAbsent(frameTime, TSShellFactory.createTSStateFromParameter(f.getId(), frameTime, p, DataState.WAITING));
                        }
                    }
                }
            }
        }
    }
}