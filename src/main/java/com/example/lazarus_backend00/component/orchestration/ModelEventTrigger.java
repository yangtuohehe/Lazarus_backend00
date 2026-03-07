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

    /** 全局状态位图大盘：FeatureID -> (时刻 -> 状态包) */
    private final Map<Integer, Map<Instant, TSState>> globalStateTable = new ConcurrentHashMap<>();

    /** 模型运行上下文注册表：RuntimeID -> 触发配置 */
    private final Map<Integer, ModelTriggerContext> registry = new ConcurrentHashMap<>();

    /** 当前系统的虚拟物理时间 */
    private Instant currentPhysicalTime;

    public ModelEventTrigger(ModelOrchestratorService orchestratorService, Instant startTime) {
        this.orchestratorService = orchestratorService;
        this.currentPhysicalTime = startTime;
        log.info("🎯 [Trigger] Initialized. System Virtual Time: {}", this.currentPhysicalTime);
    }

    // ===================================================================================
    // 原始管理功能（保留且完整）
    // ===================================================================================

    /**
     * 注册模型到触发引擎
     */
    public void registerModel(int runtimeId, List<Parameter> params, Duration step, Duration window) {
        registry.put(runtimeId, new ModelTriggerContext(runtimeId, params, step, window));
        log.info("📝 [Trigger] Model Registered. RuntimeID: {}", runtimeId);
    }

    /**
     * ✅ 恢复：注销模型功能
     */
    public void unregisterModel(int runtimeId) {
        registry.remove(runtimeId);
        log.info("🗑️ [Trigger] Unregistered Model RuntimeID: {}", runtimeId);
    }

    // ===================================================================================
    // 事件监听
    // ===================================================================================

    @EventListener
    public void onVirtualTimeTick(VirtualTimeTickEvent event) {
        this.currentPhysicalTime = event.getVirtualTime();
        log.info("📊 [State Matrix] System Time: {} | Available Features: {}",
                currentPhysicalTime, getReadyFeaturesAtInstant(currentPhysicalTime));
        evaluateAndDispatchTasks(false);
    }

    @EventListener
    public void onDataStateUpdate(DataStateUpdateEvent event) {
        for (TSState incoming : event.getTsStates()) {
            globalStateTable.computeIfAbsent(incoming.getFeatureId(), k -> new ConcurrentHashMap<>())
                    .put(incoming.getTOrigin(), new TSState(incoming));
        }
        evaluateAndDispatchTasks(event.isReplacedCorrection());
    }

    // ===================================================================================
    // 核心调度引擎 (整合 100小时回溯 + 输出步长遍历)
    // ===================================================================================

    private void evaluateAndDispatchTasks(boolean isReplacedFlow) {
        if (registry.isEmpty()) return;

        for (ModelTriggerContext ctx : registry.values()) {
            long inputOffsetSec = calculateInputOffsetSeconds(ctx);
            long outputStepSec = calculateOutputSpanSeconds(ctx);
            if (outputStepSec <= 0) outputStepSec = 3600;

            // 强制对齐时间网格
            long currentSec = currentPhysicalTime.getEpochSecond();
            long anchoredSec = currentSec - (currentSec % outputStepSec);

            // 扫描深达 100 小时
            long startSec = anchoredSec - Math.max(100 * 3600, inputOffsetSec + outputStepSec);
            startSec = startSec - (startSec % outputStepSec);

            Instant startScan = Instant.ofEpochSecond(startSec);

            log.info("⚙️ [Scan] Model: {} | InputSpan: {}s | OutputStep: {}s | ScanStart: {}",
                    ctx.getContainerId(), inputOffsetSec, outputStepSec, startScan);

            // 按输出步长跳跃遍历，防止任务重叠
            for (Instant t = startScan; !t.isAfter(currentPhysicalTime); t = t.plusSeconds(outputStepSec)) {

                log.info("   🔍 [Checking Slot] In: {} | Features: {}", t, getReadyFeaturesAtInstant(t));

                // 输出起点 = 输入起点 + 输入窗口跨度 (t + 24h)
                Instant outputStartTime = t.plusSeconds(inputOffsetSec);

                if (!checkInputsReadyStrict(ctx, t)) {
                    continue;
                }

                if (checkCalculationEligibility(ctx, outputStartTime, isReplacedFlow)) {
                    buildAndSubmitTask(ctx, t, outputStartTime, isReplacedFlow);
                    log.info("🚀 [Dispatch] TASK CREATED! Model: {}, Slot: [In: {}, Out: {}]",
                            ctx.getContainerId(), t, outputStartTime);
                }
            }
        }
    }

    // ===================================================================================
    // 内部检查逻辑
    // ===================================================================================

    private boolean checkInputsReadyStrict(ModelTriggerContext ctx, Instant baseTime) {
        for (Parameter p : ctx.getInputParams()) {
            TimeAxis tAxis = p.getTimeAxis();
            if (tAxis == null) continue;

            long resSec = getAxisResolutionInSeconds(tAxis);
            for (Feature f : p.getFeatureList()) {
                for (int i = 0; i < tAxis.getCount(); i++) {
                    Instant reqT = baseTime.plusSeconds(i * resSec);
                    Map<Instant, TSState> timeMap = globalStateTable.get(f.getId());

                    if (timeMap == null || !timeMap.containsKey(reqT)) {
                        log.warn("   🛑 [Blocked] Missing Input: FeatureID {}, Time {}, but GlobalTable is empty!", f.getId(), reqT);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean checkCalculationEligibility(ModelTriggerContext ctx, Instant outputStartTime, boolean isReplacedFlow) {
        if (isReplacedFlow) return true;
        boolean needsCalc = false;

        for (Parameter p : ctx.getOutputParams()) {
            TimeAxis tAxis = p.getTimeAxis();
            if (tAxis == null) continue;

            long resSec = getAxisResolutionInSeconds(tAxis);
            for (Feature f : p.getFeatureList()) {
                for (int i = 0; i < tAxis.getCount(); i++) {
                    Instant outT = outputStartTime.plusSeconds(i * resSec);

                    // 未来预测点不作为拦截理由
                    if (outT.isAfter(currentPhysicalTime)) {
                        continue;
                    }

                    Map<Instant, TSState> timeMap = globalStateTable.get(f.getId());
                    if (timeMap == null || !timeMap.containsKey(outT)) {
                        needsCalc = true;
                    } else {
                        TSState state = timeMap.get(outT);
                        if (state.hasHoles() || state.hasReplacedData()) {
                            needsCalc = true;
                        }
                    }
                }
            }
        }

        if (!needsCalc) {
            log.info("   💤 [Idle] Slot data is already complete, skipping calculation.");
        }
        return needsCalc;
    }

    // ===================================================================================
    // 跨度计算与辅助工具 (增加 Axis Debug 打印)
    // ===================================================================================

    private long getAxisResolutionInSeconds(TimeAxis tAxis) {
        if (tAxis == null || tAxis.getResolution() == null) return 0;
        double res = tAxis.getResolution();
        String unit = (tAxis.getUnit() != null) ? tAxis.getUnit().trim().toLowerCase() : "s";

        long finalSec = unit.startsWith("h") ? (long)(res * 3600) : (unit.startsWith("m") ? (long)(res * 60) : (long)res);

        // 🎯 关键 Debug 打印：确保 InputSpan 从 0 变成 86400
        log.info("   ⏳ [Axis Debug] Type: {}, Unit: '{}', RawRes: {}, Count: {}, Result: {}s",
                tAxis.getType(), unit, res, tAxis.getCount(), finalSec);

        return finalSec;
    }

    private long calculateInputOffsetSeconds(ModelTriggerContext ctx) {
        return ctx.getInputParams().stream()
                .mapToLong(p -> p.getTimeAxis() != null ? (long)p.getTimeAxis().getCount() * getAxisResolutionInSeconds(p.getTimeAxis()) : 0)
                .max().orElse(0);
    }

    private long calculateOutputSpanSeconds(ModelTriggerContext ctx) {
        return ctx.getOutputParams().stream()
                .mapToLong(p -> p.getTimeAxis() != null ? (long)p.getTimeAxis().getCount() * getAxisResolutionInSeconds(p.getTimeAxis()) : 0)
                .max().orElse(0);
    }

    private List<Integer> getReadyFeaturesAtInstant(Instant t) {
        List<Integer> list = new ArrayList<>();
        globalStateTable.forEach((id, map) -> { if(map.containsKey(t)) list.add(id); });
        Collections.sort(list);
        return list;
    }

    private void buildAndSubmitTask(ModelTriggerContext ctx, Instant inputBaseTime, Instant outputStartTime, boolean isReplacedFlow) {
        // 组装输入端口
        List<ExecutableTask.TaskPort> inputPorts = new ArrayList<>();
        for (Parameter p : ctx.getInputParams()) {
            List<TSShell> shells = new ArrayList<>();
            for (Feature f : p.getFeatureList()) {
                shells.add(TSShellFactory.createFromParameter(f.getId(), inputBaseTime, p));
            }
            inputPorts.add(new ExecutableTask.TaskPort(p.getTensorOrder(), shells, null));
        }

// 🎯 组装输出端口 (引入 3D 状态组装)
        List<ExecutableTask.TaskPort> outputPorts = new ArrayList<>();
        for (Parameter p : ctx.getOutputParams()) {
            List<TSShell> shells = new ArrayList<>();
            List<List<TSState>> targetStatesPerFeature = new ArrayList<>();

            // 解析这个输出端口应该有多少个时间帧
            long resSec = getAxisResolutionInSeconds(p.getTimeAxis());
            int tCount = (p.getTimeAxis() != null && p.getTimeAxis().getCount() != null) ? p.getTimeAxis().getCount() : 1;

            for (Feature f : p.getFeatureList()) {
                shells.add(TSShellFactory.createFromParameter(f.getId(), outputStartTime, p));
                Map<Instant, TSState> timeMap = globalStateTable.get(f.getId());

                List<TSState> statesForThisFeature = new ArrayList<>();

                // 🎯 严谨遍历：获取每一帧独立的 TSState
                for (int t = 0; t < tCount; t++) {
                    Instant currentFrameTime = outputStartTime.plusSeconds(t * resSec);
                    TSState currentState = (timeMap != null) ? timeMap.get(currentFrameTime) : null;

                    if (currentState == null) {
                        currentState = TSShellFactory.createTSStateFromParameter(f.getId(), currentFrameTime, p, DataState.WAITING);
                    }
                    statesForThisFeature.add(new TSState(currentState));
                }
                targetStatesPerFeature.add(statesForThisFeature);
            }
            outputPorts.add(new ExecutableTask.TaskPort(p.getTensorOrder(), shells, targetStatesPerFeature));
        }

        long taskId = taskIdGenerator.getAndIncrement();
        orchestratorService.dispatchTask(new ExecutableTask(taskId, ctx.getContainerId(), isReplacedFlow, inputPorts, outputPorts));
    }
}