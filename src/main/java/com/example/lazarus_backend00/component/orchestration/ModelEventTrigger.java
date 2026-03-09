package com.example.lazarus_backend00.component.orchestration;

import com.example.lazarus_backend00.component.clock.VirtualTimeTickEvent;
import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.domain.axis.Feature;
import com.example.lazarus_backend00.domain.axis.TimeAxis;
import com.example.lazarus_backend00.domain.data.DataState;
import com.example.lazarus_backend00.domain.data.TSState;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.domain.data.TSShellFactory;
import com.example.lazarus_backend00.service.ModelOrchestratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class ModelEventTrigger {

    private final ModelOrchestratorService orchestratorService;
    private final AtomicLong taskIdGenerator = new AtomicLong(1000);

    /** 注册表 */
    private final Map<Integer, ModelTriggerContext> registry = new ConcurrentHashMap<>();

    /** 依赖图路由：仅用于当外部数据到达时，知道该把数据派发给哪些模型的自治列表 */
    private final Map<Integer, List<ModelTriggerContext>> featureRouterMap = new ConcurrentHashMap<>();

    private Instant currentPhysicalTime;

    public ModelEventTrigger(ModelOrchestratorService orchestratorService, Instant startTime) {
        this.orchestratorService = orchestratorService;
        this.currentPhysicalTime = startTime;
    }

    // 注意：去掉了 Duration 参数
    public void registerModel(int runtimeId, List<Parameter> params) {
        ModelTriggerContext ctx = new ModelTriggerContext(runtimeId, params);
        registry.put(runtimeId, ctx);

        for (Parameter p : params) {
            for (Feature f : p.getFeatureList()) {
                featureRouterMap.computeIfAbsent(f.getId(), k -> new ArrayList<>()).add(ctx);
            }
        }
    }

    @EventListener
    public void onVirtualTimeTick(VirtualTimeTickEvent event) {
        this.currentPhysicalTime = event.getVirtualTime();
        scanAndDispatch();
    }

    @EventListener
    public void onDataStateUpdate(DataStateUpdateEvent event) {
        // 数据路由：收到外部数据，分发给所有关心它的模型沙盘
        for (TSState incoming : event.getTsStates()) {
            List<ModelTriggerContext> contexts = featureRouterMap.getOrDefault(incoming.getFeatureId(), Collections.emptyList());
            for (ModelTriggerContext ctx : contexts) {
                ctx.updateLocalState(incoming);
            }
        }
        scanAndDispatch();
    }

    // ===================================================================================
    // 算法核心：以每个模型为自治单元，在一个时间步一个时间步地推演 (支持 oTimeStep 偏移)
    // ===================================================================================
    private void scanAndDispatch() {
        for (ModelTriggerContext ctx : registry.values()) {

            // 计算这个模型的基本推进步长（取输出中最小的分辨率）
            long baseStepSec = calculateBaseStepSeconds(ctx.getOutputParams());
            // 一次任务最大跨度，用于发现缺失时的跳跃跳过 (Jump)
            long maxOutputSpanSec = calculateMaxSpanSeconds(ctx.getOutputParams());

            // 回溯100小时进行对齐遍历
            long startSec = currentPhysicalTime.getEpochSecond() - (100 * 3600);
            startSec = startSec - (startSec % baseStepSec);
            Instant modelTime = Instant.ofEpochSecond(startSec);

            // 1. 预先确保沙盘里存在 0 (WAITING) 的空洞占位
            ensureZerosExistInContext(ctx, modelTime, currentPhysicalTime);

            // 2. 一个时间步一个时间步地遍历
            while (!modelTime.isAfter(currentPhysicalTime)) {

                // 检查当前 modelTime 下，输入数据的状态 (0, 1, 2)
                int inputStatus = checkInputsStatus(ctx, modelTime);

                // ----------------------------------------------------------------
                // 第二路：发现替换态 (2) -> 只要作为输入，强制全量重算！(忽略输出是否有空洞)
                // ----------------------------------------------------------------
                if (inputStatus == 2) {
                    buildAndSubmitTask(ctx, modelTime, false); // applyMask = false，不生成掩膜
                    changeInputStates2To1(ctx, modelTime);     // 把输入里的 2 清除，防止死循环
                    changeOutputStates0To1(ctx, modelTime);    // 提前把输出坑位占上

                    // 🎯 核心逻辑：触发任务后，跳过此次任务的输出时间段！
                    modelTime = modelTime.plusSeconds(maxOutputSpanSec);
                    continue;
                }

                // ----------------------------------------------------------------
                // 第一路：发现空态 (0) -> 如果数据齐全且全是1，补漏计算！
                // ----------------------------------------------------------------
                if (hasOutputHoles(ctx, modelTime)) {
                    if (inputStatus == 1) {
                        buildAndSubmitTask(ctx, modelTime, true); // applyMask = true，生成掩膜保护已有数据
                        changeOutputStates0To1(ctx, modelTime);   // 提前占位

                        // 🎯 核心逻辑：跳过此次任务的输出时间段！
                        modelTime = modelTime.plusSeconds(maxOutputSpanSec);
                        continue;
                    }
                }

                // 常规步进
                modelTime = modelTime.plusSeconds(baseStepSec);
            }
        }
    }

    // ===================================================================================
    // 算法辅助：带 oTimeStep 的时空定位 (支持多特征)
    // ===================================================================================

    // 返回值：0(不齐), 1(齐且全1), 2(齐且含2)
    private int checkInputsStatus(ModelTriggerContext ctx, Instant modelTime) {
        boolean hasState2 = false;

        for (Parameter p : ctx.getInputParams()) {
            TimeAxis tAxis = p.getTimeAxis();
            if (tAxis == null) continue;
            long resSec = getAxisResolutionInSeconds(tAxis);

            // 🎯 核心：利用 oTimeStep 确定真实的输入起算时间！
            int offset = (p.getoTimeStep() != null) ? p.getoTimeStep() : 0;
            Instant actualStartTime = modelTime.plusSeconds(offset * resSec);

            for (Feature f : p.getFeatureList()) {
                for (int i = 0; i < tAxis.getCount(); i++) {
                    Instant reqT = actualStartTime.plusSeconds(i * resSec);
                    TSState state = ctx.getLocalState(f.getId(), reqT);

                    if (state == null || state.hasHoles()) return 0; // 输入有空缺
                    if (state.hasReplacedData()) hasState2 = true;   // 含有 2
                }
            }
        }
        return hasState2 ? 2 : 1;
    }

    private boolean hasOutputHoles(ModelTriggerContext ctx, Instant modelTime) {
        for (Parameter p : ctx.getOutputParams()) {
            TimeAxis tAxis = p.getTimeAxis();
            if (tAxis == null) continue;
            long resSec = getAxisResolutionInSeconds(tAxis);

            int offset = (p.getoTimeStep() != null) ? p.getoTimeStep() : 0;
            Instant actualStartTime = modelTime.plusSeconds(offset * resSec);

            for (Feature f : p.getFeatureList()) {
                for (int i = 0; i < tAxis.getCount(); i++) {
                    Instant outT = actualStartTime.plusSeconds(i * resSec);
                    if (outT.isAfter(currentPhysicalTime)) continue; // 未来不视为空洞

                    TSState state = ctx.getLocalState(f.getId(), outT);
                    if (state == null || state.hasHoles()) return true; // 发现任何一个特征有空洞就返回true
                }
            }
        }
        return false;
    }

    private void changeInputStates2To1(ModelTriggerContext ctx, Instant modelTime) {
        for (Parameter p : ctx.getInputParams()) {
            TimeAxis tAxis = p.getTimeAxis();
            if (tAxis == null) continue;
            long resSec = getAxisResolutionInSeconds(tAxis);
            int offset = (p.getoTimeStep() != null) ? p.getoTimeStep() : 0;
            Instant actualStartTime = modelTime.plusSeconds(offset * resSec);

            for (Feature f : p.getFeatureList()) {
                for (int i = 0; i < tAxis.getCount(); i++) {
                    Instant reqT = actualStartTime.plusSeconds(i * resSec);
                    TSState state = ctx.getLocalState(f.getId(), reqT);
                    if (state != null && state.hasReplacedData()) {
                        state.getReplacedMask().clear();
                        state.getReadyMask().set(0, calculateFrameSize(p));
                    }
                }
            }
        }
    }

    private void changeOutputStates0To1(ModelTriggerContext ctx, Instant modelTime) {
        for (Parameter p : ctx.getOutputParams()) {
            TimeAxis tAxis = p.getTimeAxis();
            if (tAxis == null) continue;
            long resSec = getAxisResolutionInSeconds(tAxis);
            int offset = (p.getoTimeStep() != null) ? p.getoTimeStep() : 0;
            Instant actualStartTime = modelTime.plusSeconds(offset * resSec);

            for (Feature f : p.getFeatureList()) {
                for (int i = 0; i < tAxis.getCount(); i++) {
                    Instant outT = actualStartTime.plusSeconds(i * resSec);
                    TSState state = ctx.getLocalState(f.getId(), outT);
                    if (state != null) {
                        state.getReadyMask().set(0, calculateFrameSize(p));
                    }
                }
            }
        }
    }

    private void buildAndSubmitTask(ModelTriggerContext ctx, Instant modelTime, boolean applyMask) {
        List<ExecutableTask.TaskPort> inputPorts = new ArrayList<>();
        for (Parameter p : ctx.getInputParams()) {
            long resSec = getAxisResolutionInSeconds(p.getTimeAxis());
            int offset = (p.getoTimeStep() != null) ? p.getoTimeStep() : 0;
            Instant actualStartTime = modelTime.plusSeconds(offset * resSec);

            List<TSShell> shells = new ArrayList<>();
            for (Feature f : p.getFeatureList()) {
                shells.add(TSShellFactory.createFromParameter(f.getId(), actualStartTime, p));
            }
            inputPorts.add(new ExecutableTask.TaskPort(p.getTensorOrder(), shells, null));
        }

        List<ExecutableTask.TaskPort> outputPorts = new ArrayList<>();
        for (Parameter p : ctx.getOutputParams()) {
            long resSec = getAxisResolutionInSeconds(p.getTimeAxis());
            int offset = (p.getoTimeStep() != null) ? p.getoTimeStep() : 0;
            Instant actualStartTime = modelTime.plusSeconds(offset * resSec);
            int tCount = (p.getTimeAxis() != null && p.getTimeAxis().getCount() != null) ? p.getTimeAxis().getCount() : 1;

            List<TSShell> shells = new ArrayList<>();
            List<List<TSState>> targetStatesPerFeature = new ArrayList<>();

            for (Feature f : p.getFeatureList()) {
                shells.add(TSShellFactory.createFromParameter(f.getId(), actualStartTime, p));

                List<TSState> statesForThisFeature = new ArrayList<>();
                for (int t = 0; t < tCount; t++) {
                    Instant currentFrameTime = actualStartTime.plusSeconds(t * resSec);
                    TSState currentState = ctx.getLocalState(f.getId(), currentFrameTime);
                    // 深拷贝局部沙盘中的原始真实状态，作为掩膜送入任务
                    statesForThisFeature.add(new TSState(currentState));
                }
                targetStatesPerFeature.add(statesForThisFeature);
            }
            outputPorts.add(new ExecutableTask.TaskPort(p.getTensorOrder(), shells, targetStatesPerFeature));
        }

        long taskId = taskIdGenerator.getAndIncrement();
        orchestratorService.dispatchTask(new ExecutableTask(taskId, ctx.getContainerId(), applyMask, inputPorts, outputPorts));
    }

    private void ensureZerosExistInContext(ModelTriggerContext ctx, Instant start, Instant end) {
        // 根据输出参数定义，在模型沙盘里铺设 WAITING 状态，诱导扫盘算法
        for (Parameter p : ctx.getOutputParams()) {
            TimeAxis tAxis = p.getTimeAxis();
            if(tAxis == null) continue;
            long resSec = getAxisResolutionInSeconds(tAxis);
            for (Instant t = start; !t.isAfter(end); t = t.plusSeconds(resSec)) {
                for (Feature f : p.getFeatureList()) {
                    ctx.ensureStateExists(f.getId(), t, p);
                }
            }
        }
    }

    private long calculateBaseStepSeconds(List<Parameter> params) {
        return params.stream()
                .mapToLong(p -> getAxisResolutionInSeconds(p.getTimeAxis()))
                .filter(res -> res > 0)
                .min().orElse(3600);
    }

    private long calculateMaxSpanSeconds(List<Parameter> params) {
        return params.stream()
                .mapToLong(p -> p.getTimeAxis() != null ? (long)p.getTimeAxis().getCount() * getAxisResolutionInSeconds(p.getTimeAxis()) : 3600)
                .max().orElse(3600);
    }

    private int calculateFrameSize(Parameter p) {
        int w = 1, h = 1;
        if (p.getAxisList() != null) {
            for (com.example.lazarus_backend00.domain.axis.Axis axis : p.getAxisList()) {
                if (axis instanceof com.example.lazarus_backend00.domain.axis.SpaceAxisX && axis.getCount() != null) w = axis.getCount();
                else if (axis instanceof com.example.lazarus_backend00.domain.axis.SpaceAxisY && axis.getCount() != null) h = axis.getCount();
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
     * 注销模型功能 (同时安全清理沙盘路由表，防止内存泄漏)
     */
    public void unregisterModel(int runtimeId) {
        ModelTriggerContext ctx = registry.remove(runtimeId);
        if (ctx != null) {
            // 清理输入特征路由
            for (Parameter p : ctx.getInputParams()) {
                for (Feature f : p.getFeatureList()) {
                    List<ModelTriggerContext> list = featureRouterMap.get(f.getId());
                    if (list != null) {
                        list.removeIf(c -> c.getContainerId() == runtimeId);
                        if (list.isEmpty()) featureRouterMap.remove(f.getId());
                    }
                }
            }
            // 清理输出特征路由
            for (Parameter p : ctx.getOutputParams()) {
                for (Feature f : p.getFeatureList()) {
                    List<ModelTriggerContext> list = featureRouterMap.get(f.getId());
                    if (list != null) {
                        list.removeIf(c -> c.getContainerId() == runtimeId);
                        if (list.isEmpty()) featureRouterMap.remove(f.getId());
                    }
                }
            }
            log.info("🗑️ [Trigger] Unregistered Model & Cleaned Router. RuntimeID: {}", runtimeId);
        } else {
            log.warn("⚠️ [Trigger] Model not found when unregistering. RuntimeID: {}", runtimeId);
        }
    }
}