package com.example.lazarus_backend00.component.orchestration;

import com.example.lazarus_backend00.component.clock.VirtualTimeTickEvent;
import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.domain.axis.Axis;
import com.example.lazarus_backend00.domain.axis.Feature;
import com.example.lazarus_backend00.domain.axis.TimeAxis;
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
        printGlobalTimelineDashboard();
    }

    @EventListener
    public void onDataStateUpdate(DataStateUpdateEvent event) {
        for (TSState incoming : event.getTsStates()) {
            List<ModelTriggerContext> contexts = featureRouterMap.getOrDefault(incoming.getFeatureId(), Collections.emptyList());
            for (ModelTriggerContext ctx : contexts) {
                ctx.updateLocalState(incoming);
            }
        }
        scanAndDispatch();
        printGlobalTimelineDashboard();
    }

    /**
     * 🎯 当底层模型容器算完之后，调用这个方法清除任务记录锁！
     */
    public void notifyTaskCompleted(int runtimeId, Instant taskModelTime) {
        ModelTriggerContext ctx = registry.get(runtimeId);
        if (ctx != null) {
            ctx.removeTaskRecord(taskModelTime);
        }
    }

    // ===================================================================================
    // 算法核心：双路两循环 (逆向寻优填补 + 正向同化纠偏)
    // ===================================================================================
    private void scanAndDispatch() {
        for (ModelTriggerContext ctx : registry.values()) {

            long baseStepSec = calculateBaseStepSeconds(ctx.getOutputParams());
            long currentSec = currentPhysicalTime.getEpochSecond();

            // 划定扫描的边界：[过去100小时, 未来100小时]
            long futureSec = currentSec + (100 * 3600);
            futureSec = futureSec - (futureSec % baseStepSec);
            Instant futureBound = Instant.ofEpochSecond(futureSec);

            long pastSec = currentSec - (100 * 3600);
            pastSec = pastSec - (pastSec % baseStepSec);
            Instant pastBound = Instant.ofEpochSecond(pastSec);

            // 预填0坑位
            ensureZerosExistInContext(ctx, pastBound, futureBound);

            // ----------------------------------------------------------------
            // 路径一：【逆向时空扫描】 (专职空缺填补)
            // ----------------------------------------------------------------
            Instant backCursor = futureBound;
            while (!backCursor.isBefore(pastBound)) {
                int inputStatus = checkInputsStatus(ctx, backCursor);

                // 输入就绪且输出有空洞
                if (inputStatus == 1 && hasOutputHoles(ctx, backCursor)) {
                    buildAndSubmitTask(ctx, backCursor, true); // applyMask = true
                    ctx.addTaskRecord(backCursor); // ★ 写入记录，锁住这个时间步
                    break; // 发车即退
                }
                backCursor = backCursor.minusSeconds(baseStepSec); // 逆向减步长
            }

            // ----------------------------------------------------------------
            // 路径二：【正向时空扫描】 (专职状态替换重算)
            // ----------------------------------------------------------------
            Instant fwdCursor = pastBound;
            while (!fwdCursor.isAfter(futureBound)) {
                int inputStatus = checkInputsStatus(ctx, fwdCursor);

                // 发现实测替换态
                if (inputStatus == 2) {
                    // ★ 查重：如果任务记录表里没有这个时间步的任务在跑，才允许重算
                    if (!ctx.hasTaskRecord(fwdCursor)) {
                        buildAndSubmitTask(ctx, fwdCursor, false); // applyMask = false
                        changeInputStates2To1(ctx, fwdCursor);     // 降级防死循环
                        break; // 发车即退
                    }
                }
                fwdCursor = fwdCursor.plusSeconds(baseStepSec); // 正向加步长
            }
        }
    }

    // ===================================================================================
    // 算法辅助方法
    // ===================================================================================
    private int checkInputsStatus(ModelTriggerContext ctx, Instant modelTime) {
        boolean hasState2 = false;

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

                    if (state == null || state.hasHoles()) return 0;
                    if (state.hasReplacedData()) hasState2 = true;
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
                    TSState state = ctx.getLocalState(f.getId(), outT);
                    if (state == null || state.hasHoles()) return true;
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

    private int calculateFrameSize(Parameter p) {
        int w = 1, h = 1;
        if (p.getAxisList() != null) {
            for (Axis axis : p.getAxisList()) {
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

    // ===================================================================================
    // 被误删的注销方法和打印方法，已完整找回
    // ===================================================================================
    public void unregisterModel(int runtimeId) {
        ModelTriggerContext ctx = registry.remove(runtimeId);
        if (ctx != null) {
            for (Parameter p : ctx.getInputParams()) {
                for (Feature f : p.getFeatureList()) {
                    List<ModelTriggerContext> list = featureRouterMap.get(f.getId());
                    if (list != null) {
                        list.removeIf(c -> c.getContainerId() == runtimeId);
                        if (list.isEmpty()) featureRouterMap.remove(f.getId());
                    }
                }
            }
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

    private synchronized void printGlobalTimelineDashboard() {
        System.out.println("\n================ 🌍 孪生沙盘：时间轴数据状态大盘 ================");
        System.out.println("⏰ 当前数字孪生系统时钟: " + currentPhysicalTime);

        Set<Integer> allFeatures = new TreeSet<>(featureRouterMap.keySet());
        if (allFeatures.isEmpty()) {
            System.out.println("暂无特征注册。");
            System.out.println("=================================================================\n");
            return;
        }

        Instant startT = currentPhysicalTime.minusSeconds(48 * 3600);

        for (Instant t = startT; !t.isAfter(currentPhysicalTime); t = t.plusSeconds(3600)) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("🗓️ 时刻: %s | ", t.toString()));

            for (Integer fId : allFeatures) {
                String status = "⚪ WAITING(0) [空缺]";
                if (featureRouterMap.get(fId) != null) {
                    for (ModelTriggerContext ctx : featureRouterMap.get(fId)) {
                        TSState state = ctx.getLocalState(fId, t);
                        if (state != null) {
                            if (state.hasReplacedData()) {
                                status = "🟢 REPLACED(2) [实测]";
                                break;
                            } else if (!state.hasHoles()) {
                                status = "🔵 READY(1) [预测]";
                                break;
                            }
                        }
                    }
                }
                sb.append(String.format("特征%d: %s   ", fId, status));
            }
            System.out.println(sb.toString());
        }
        System.out.println("=================================================================\n");
    }
}
