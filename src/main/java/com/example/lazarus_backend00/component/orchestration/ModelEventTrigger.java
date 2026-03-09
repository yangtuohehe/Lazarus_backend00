//
//package com.example.lazarus_backend00.component.orchestration;
//
//import com.example.lazarus_backend00.component.clock.VirtualTimeTickEvent;
//import com.example.lazarus_backend00.component.container.Parameter;
//import com.example.lazarus_backend00.domain.axis.Axis;
//import com.example.lazarus_backend00.domain.axis.Feature;
//import com.example.lazarus_backend00.domain.axis.TimeAxis;
//import com.example.lazarus_backend00.domain.data.TSState;
//import com.example.lazarus_backend00.domain.data.TSShell;
//import com.example.lazarus_backend00.domain.data.TSShellFactory;
//import com.example.lazarus_backend00.service.ModelOrchestratorService;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.context.event.EventListener;
//
//import java.time.Instant;
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.atomic.AtomicLong;
//
//@Slf4j
//public class ModelEventTrigger {
//
//    private final ModelOrchestratorService orchestratorService;
//    private final AtomicLong taskIdGenerator = new AtomicLong(1000);
//
//    /** 注册表 */
//    private final Map<Integer, ModelTriggerContext> registry = new ConcurrentHashMap<>();
//
//    /** 依赖图路由：仅用于当外部数据到达时，知道该把数据派发给哪些模型的自治列表 */
//    private final Map<Integer, List<ModelTriggerContext>> featureRouterMap = new ConcurrentHashMap<>();
//
//    private Instant currentPhysicalTime;
//
//    public ModelEventTrigger(ModelOrchestratorService orchestratorService, Instant startTime) {
//        this.orchestratorService = orchestratorService;
//        this.currentPhysicalTime = startTime;
//    }
//
//    public void registerModel(int runtimeId, List<Parameter> params) {
//        ModelTriggerContext ctx = new ModelTriggerContext(runtimeId, params);
//        registry.put(runtimeId, ctx);
//
//        for (Parameter p : params) {
//            for (Feature f : p.getFeatureList()) {
//                featureRouterMap.computeIfAbsent(f.getId(), k -> new ArrayList<>()).add(ctx);
//            }
//        }
//    }
//
//    @EventListener
//    public void onVirtualTimeTick(VirtualTimeTickEvent event) {
//        this.currentPhysicalTime = event.getVirtualTime();
//        scanAndDispatch();
//
//        // 👉 上帝视角状态大盘日志
//        printGlobalTimelineDashboard();
//    }
//
//    @EventListener
//    public void onDataStateUpdate(DataStateUpdateEvent event) {
//        // 数据路由：收到外部数据，分发给所有关心它的模型沙盘
//        for (TSState incoming : event.getTsStates()) {
//            List<ModelTriggerContext> contexts = featureRouterMap.getOrDefault(incoming.getFeatureId(), Collections.emptyList());
//            for (ModelTriggerContext ctx : contexts) {
//                ctx.updateLocalState(incoming);
//            }
//        }
//        scanAndDispatch();
//
//        // 👉 上帝视角状态大盘日志
//        printGlobalTimelineDashboard();
//    }
//
//    // ===================================================================================
//    // 算法核心：以每个模型为自治单元，纯状态驱动推演 (无强制跳跃)
//    // ===================================================================================
//    private void scanAndDispatch() {
//        for (ModelTriggerContext ctx : registry.values()) {
//
//            long baseStepSec = calculateBaseStepSeconds(ctx.getOutputParams());
//
//            // 回溯100小时进行对齐遍历
//            long startSec = currentPhysicalTime.getEpochSecond() - (100 * 3600);
//            startSec = startSec - (startSec % baseStepSec);
//            Instant modelTime = Instant.ofEpochSecond(startSec);
//
//            // 1. 预先确保沙盘里存在 0 (WAITING) 的空洞占位
//            ensureZerosExistInContext(ctx, modelTime, currentPhysicalTime);
//
//            // 2. 一个时间步一个时间步地纯步进遍历（完全由状态拦截，无需大跳）
//            while (!modelTime.isAfter(currentPhysicalTime)) {
//
//                int inputStatus = checkInputsStatus(ctx, modelTime);
//
////                // ----------------------------------------------------------------
////                // 第二路：发现实测替换态 (2) -> 只要作为输入，强制重算！
////                // ----------------------------------------------------------------
////                if (inputStatus == 2) {
////                    buildAndSubmitTask(ctx, modelTime, false);
////
////                    // 🎯 核心修复：只清空当前发车帧的实测标记，绝对不碰后面的实测数据 (解决3变2问题)
////                    changeInputStates2To1ForCurrentCursorOnly(ctx, modelTime);
////                    // 🎯 提前占位：在本地沙盘里把未来输出设为1，防止下一步循环重复发车
////                    //changeOutputStates0To1(ctx, modelTime);
////                }
////                // ----------------------------------------------------------------
////                // 第一路：发现数据齐全 (1) 且输出首帧为空态 (0) -> 补漏计算！
////                // ----------------------------------------------------------------
////                else if (inputStatus == 1 && hasAnyOutputHole(ctx, modelTime)) {
////                    buildAndSubmitTask(ctx, modelTime, true);
////
////                    // 🎯 提前占位：在本地沙盘里把未来输出设为1，防止下一步循环重复发车
////                    //changeOutputStates0To1(ctx, modelTime);
////                }
//// ----------------------------------------------------------------
//                // 第二路：发现实测替换态 (2) -> 只要作为输入，强制重算！
//                // ----------------------------------------------------------------
//                if (inputStatus == 2) {
//                    buildAndSubmitTask(ctx, modelTime, false);
//                    changeInputStates2To1ForCurrentCursorOnly(ctx, modelTime);
//
//                    // ❌ 删除：changeOutputStates0To1(ctx, modelTime);
//                    break; // ✅ 新增：发车后立刻刹车，等待真实任务完成后的广播唤醒！
//                }
//                // ----------------------------------------------------------------
//                // 第一路：发现数据齐全 (1) 且输出首帧为空态 (0) -> 补漏计算！
//                // ----------------------------------------------------------------
//                else if (inputStatus == 1 && hasAnyOutputHole(ctx, modelTime)) {
//                    buildAndSubmitTask(ctx, modelTime, true);
//
//                    // ❌ 删除：changeOutputStates0To1(ctx, modelTime);
//                    break; // ✅ 新增：发车后立刻刹车，等待真实任务完成后的广播唤醒！
//                }
//                // 常规步进：永远只走基础步长，靠上述逻辑自然拦截重叠
//                modelTime = modelTime.plusSeconds(baseStepSec);
//            }
//        }
//    }
//
//    private int checkInputsStatus(ModelTriggerContext ctx, Instant modelTime) {
//        boolean hasState2 = false;
//
//        for (Parameter p : ctx.getInputParams()) {
//            TimeAxis tAxis = p.getTimeAxis();
//            if (tAxis == null) continue;
//            long resSec = getAxisResolutionInSeconds(tAxis);
//
//            int offset = (p.getoTimeStep() != null) ? p.getoTimeStep() : 0;
//            Instant actualStartTime = modelTime.plusSeconds(offset * resSec);
//
//            for (Feature f : p.getFeatureList()) {
//                for (int i = 0; i < tAxis.getCount(); i++) {
//                    Instant reqT = actualStartTime.plusSeconds(i * resSec);
//                    TSState state = ctx.getLocalState(f.getId(), reqT);
//
//                    if (state == null || state.hasHoles()) return 0;
//                    if (state.hasReplacedData()) hasState2 = true;
//                }
//            }
//        }
//        return hasState2 ? 2 : 1;
//    }
//    /**
//     * 🎯 严格分块防线：只检查这一趟输出的【第一帧】！
//     * 只要第一帧有数据，说明整个块已经被上一个任务覆盖过了，游标静默滑过，直到遇到下一个真实的空洞起点！
//     */
//    private boolean isFirstOutputMomentAHole(ModelTriggerContext ctx, Instant modelTime) {
//        for (Parameter p : ctx.getOutputParams()) {
//            TimeAxis tAxis = p.getTimeAxis();
//            if (tAxis == null) continue;
//
//            long resSec = getAxisResolutionInSeconds(tAxis);
//            int offset = (p.getoTimeStep() != null) ? p.getoTimeStep() : 0;
//            Instant firstOutT = modelTime.plusSeconds(offset * resSec);
//
//            for (Feature f : p.getFeatureList()) {
//                TSState state = ctx.getLocalState(f.getId(), firstOutT);
//                if (state == null || state.hasHoles()) return true;
//            }
//        }
//        return false;
//    }
//    /**
//     * 🎯 终极防线：只要这趟发车的输出窗口里，包含任何一个空洞，就说明它能推进边界，必须发车！
//     * （多余的重叠帧会被底层存储的掩膜拦截，绝对安全）
//     */
//    private boolean hasAnyOutputHole(ModelTriggerContext ctx, Instant modelTime) {
//        for (Parameter p : ctx.getOutputParams()) {
//            TimeAxis tAxis = p.getTimeAxis();
//            if (tAxis == null) continue;
//
//            long resSec = getAxisResolutionInSeconds(tAxis);
//            int offset = (p.getoTimeStep() != null) ? p.getoTimeStep() : 0;
//            Instant actualStartTime = modelTime.plusSeconds(offset * resSec);
//
//            for (Feature f : p.getFeatureList()) {
//                for (int i = 0; i < tAxis.getCount(); i++) {
//                    Instant outT = actualStartTime.plusSeconds(i * resSec);
//                    TSState state = ctx.getLocalState(f.getId(), outT);
//                    // 只要发现输出序列里有任何一帧是空的，立刻返回 true 触发任务
//                    if (state == null || state.hasHoles()) return true;
//                }
//            }
//        }
//        return false;
//    }
//    /**
//     * 🎯 精准清除法：只清除游标所在的第一帧，绝不能用循环去清除整个窗口
//     */
//    private void changeInputStates2To1ForCurrentCursorOnly(ModelTriggerContext ctx, Instant modelTime) {
//        for (Parameter p : ctx.getInputParams()) {
//            TimeAxis tAxis = p.getTimeAxis();
//            if (tAxis == null) continue;
//            long resSec = getAxisResolutionInSeconds(tAxis);
//            int offset = (p.getoTimeStep() != null) ? p.getoTimeStep() : 0;
//
//            // 精确算出游标对应起点的这一帧时间
//            Instant firstReqT = modelTime.plusSeconds(offset * resSec);
//
//            for (Feature f : p.getFeatureList()) {
//                TSState state = ctx.getLocalState(f.getId(), firstReqT);
//                if (state != null && state.hasReplacedData()) {
//                    state.getReplacedMask().clear();
//                    state.getReadyMask().set(0, calculateFrameSize(p));
//                }
//            }
//        }
//    }
////    private void changeOutputStates0To1(ModelTriggerContext ctx, Instant modelTime) {
////        for (Parameter p : ctx.getOutputParams()) {
////            TimeAxis tAxis = p.getTimeAxis();
////            if (tAxis == null) continue;
////            long resSec = getAxisResolutionInSeconds(tAxis);
////            int offset = (p.getoTimeStep() != null) ? p.getoTimeStep() : 0;
////            Instant actualStartTime = modelTime.plusSeconds(offset * resSec);
////
////            for (Feature f : p.getFeatureList()) {
////                for (int i = 0; i < tAxis.getCount(); i++) {
////                    Instant outT = actualStartTime.plusSeconds(i * resSec);
////
////                    // 🎯 核心修复：强制在沙盘中为未来的预测时刻开辟坑位！
////                    // 只有确保坑位存在，我们才能把它标记为“提前占位(1)”
////                    ctx.ensureStateExists(f.getId(), outT, p);
////
////                    TSState state = ctx.getLocalState(f.getId(), outT);
////                    if (state != null) {
////                        state.getReadyMask().set(0, calculateFrameSize(p));
////                    }
////                }
////            }
////        }
////    }
//
//
//    private void buildAndSubmitTask(ModelTriggerContext ctx, Instant modelTime, boolean applyMask) {
//        List<ExecutableTask.TaskPort> inputPorts = new ArrayList<>();
//        for (Parameter p : ctx.getInputParams()) {
//            long resSec = getAxisResolutionInSeconds(p.getTimeAxis());
//            int offset = (p.getoTimeStep() != null) ? p.getoTimeStep() : 0;
//            Instant actualStartTime = modelTime.plusSeconds(offset * resSec);
//
//            List<TSShell> shells = new ArrayList<>();
//            for (Feature f : p.getFeatureList()) {
//                shells.add(TSShellFactory.createFromParameter(f.getId(), actualStartTime, p));
//            }
//            inputPorts.add(new ExecutableTask.TaskPort(p.getTensorOrder(), shells, null));
//        }
//
//        List<ExecutableTask.TaskPort> outputPorts = new ArrayList<>();
//        for (Parameter p : ctx.getOutputParams()) {
//            long resSec = getAxisResolutionInSeconds(p.getTimeAxis());
//            int offset = (p.getoTimeStep() != null) ? p.getoTimeStep() : 0;
//            Instant actualStartTime = modelTime.plusSeconds(offset * resSec);
//            int tCount = (p.getTimeAxis() != null && p.getTimeAxis().getCount() != null) ? p.getTimeAxis().getCount() : 1;
//
//            List<TSShell> shells = new ArrayList<>();
//            List<List<TSState>> targetStatesPerFeature = new ArrayList<>();
//
//            for (Feature f : p.getFeatureList()) {
//                shells.add(TSShellFactory.createFromParameter(f.getId(), actualStartTime, p));
//
//                List<TSState> statesForThisFeature = new ArrayList<>();
//                for (int t = 0; t < tCount; t++) {
//                    Instant currentFrameTime = actualStartTime.plusSeconds(t * resSec);
//                    TSState currentState = ctx.getLocalState(f.getId(), currentFrameTime);
//                    statesForThisFeature.add(new TSState(currentState));
//                }
//                targetStatesPerFeature.add(statesForThisFeature);
//            }
//            outputPorts.add(new ExecutableTask.TaskPort(p.getTensorOrder(), shells, targetStatesPerFeature));
//        }
//
//        long taskId = taskIdGenerator.getAndIncrement();
//        orchestratorService.dispatchTask(new ExecutableTask(taskId, ctx.getContainerId(), applyMask, inputPorts, outputPorts));
//    }
//
//    private void ensureZerosExistInContext(ModelTriggerContext ctx, Instant start, Instant end) {
//        for (Parameter p : ctx.getOutputParams()) {
//            TimeAxis tAxis = p.getTimeAxis();
//            if(tAxis == null) continue;
//            long resSec = getAxisResolutionInSeconds(tAxis);
//            for (Instant t = start; !t.isAfter(end); t = t.plusSeconds(resSec)) {
//                for (Feature f : p.getFeatureList()) {
//                    ctx.ensureStateExists(f.getId(), t, p);
//                }
//            }
//        }
//    }
//
//    private long calculateBaseStepSeconds(List<Parameter> params) {
//        return params.stream()
//                .mapToLong(p -> getAxisResolutionInSeconds(p.getTimeAxis()))
//                .filter(res -> res > 0)
//                .min().orElse(3600);
//    }
//
//    private int calculateFrameSize(Parameter p) {
//        int w = 1, h = 1;
//        if (p.getAxisList() != null) {
//            for (com.example.lazarus_backend00.domain.axis.Axis axis : p.getAxisList()) {
//                if (axis instanceof com.example.lazarus_backend00.domain.axis.SpaceAxisX && axis.getCount() != null) w = axis.getCount();
//                else if (axis instanceof com.example.lazarus_backend00.domain.axis.SpaceAxisY && axis.getCount() != null) h = axis.getCount();
//            }
//        }
//        return w * h;
//    }
//
//    private long getAxisResolutionInSeconds(TimeAxis tAxis) {
//        if (tAxis == null || tAxis.getResolution() == null) return 0;
//        double res = tAxis.getResolution();
//        String unit = (tAxis.getUnit() != null) ? tAxis.getUnit().trim().toLowerCase() : "s";
//        return unit.startsWith("h") ? (long)(res * 3600) : (unit.startsWith("m") ? (long)(res * 60) : (long)res);
//    }
//
//    public void unregisterModel(int runtimeId) {
//        ModelTriggerContext ctx = registry.remove(runtimeId);
//        if (ctx != null) {
//            for (Parameter p : ctx.getInputParams()) {
//                for (Feature f : p.getFeatureList()) {
//                    List<ModelTriggerContext> list = featureRouterMap.get(f.getId());
//                    if (list != null) {
//                        list.removeIf(c -> c.getContainerId() == runtimeId);
//                        if (list.isEmpty()) featureRouterMap.remove(f.getId());
//                    }
//                }
//            }
//            for (Parameter p : ctx.getOutputParams()) {
//                for (Feature f : p.getFeatureList()) {
//                    List<ModelTriggerContext> list = featureRouterMap.get(f.getId());
//                    if (list != null) {
//                        list.removeIf(c -> c.getContainerId() == runtimeId);
//                        if (list.isEmpty()) featureRouterMap.remove(f.getId());
//                    }
//                }
//            }
//            log.info("🗑️ [Trigger] Unregistered Model & Cleaned Router. RuntimeID: {}", runtimeId);
//        } else {
//            log.warn("⚠️ [Trigger] Model not found when unregistering. RuntimeID: {}", runtimeId);
//        }
//    }
//
//    // ===================================================================================
//    // 🌍 [上帝视角] 打印真正的全局时间轴数据状态大盘 (跟随物理时钟推进)
//    // ===================================================================================
//    // 🎯 新增 synchronized 关键字，防止多线程异步广播时抢占控制台导致打印顺序错乱
//    private synchronized void printGlobalTimelineDashboard() {
//        System.out.println("\n================ 🌍 孪生沙盘：时间轴数据状态大盘 ================");
//        System.out.println("⏰ 当前数字孪生系统时钟: " + currentPhysicalTime);
//
//        Set<Integer> allFeatures = new TreeSet<>(featureRouterMap.keySet());
//        if (allFeatures.isEmpty()) {
//            System.out.println("暂无特征注册。");
//            System.out.println("=================================================================\n");
//            return;
//        }
//
//        Instant startT = currentPhysicalTime.minusSeconds(48 * 3600);
//
//        for (Instant t = startT; !t.isAfter(currentPhysicalTime); t = t.plusSeconds(3600)) {
//            StringBuilder sb = new StringBuilder();
//            sb.append(String.format("🗓️ 时刻: %s | ", t.toString()));
//
//            for (Integer fId : allFeatures) {
//                String status = "⚪ WAITING(0) [空缺]";
//                if (featureRouterMap.get(fId) != null) {
//                    for (ModelTriggerContext ctx : featureRouterMap.get(fId)) {
//                        TSState state = ctx.getLocalState(fId, t);
//                        if (state != null) {
//                            if (state.hasReplacedData()) {
//                                status = "🟢 REPLACED(2) [实测]";
//                                break;
//                            } else if (!state.hasHoles()) {
//                                status = "🔵 READY(1) [预测]";
//                                break;
//                            }
//                        }
//                    }
//                }
//                sb.append(String.format("特征%d: %s   ", fId, status));
//            }
//            System.out.println(sb.toString());
//        }
//        System.out.println("=================================================================\n");
//    }
//}


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

        // 👉 上帝视角状态大盘日志
        printGlobalTimelineDashboard();
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

        // 👉 上帝视角状态大盘日志
        printGlobalTimelineDashboard();
    }

    // ===================================================================================
    // 算法核心：【回溯算法】从未来向历史倒推，贪婪搜索最优起算点！
    // ===================================================================================
    private void scanAndDispatch() {
        for (ModelTriggerContext ctx : registry.values()) {

            long baseStepSec = calculateBaseStepSeconds(ctx.getOutputParams());
            long currentSec = currentPhysicalTime.getEpochSecond();

            // 1. 设定回溯起点：从未来的 100 小时开始，往回倒推！
            long startSec = currentSec + (100 * 3600);
            startSec = startSec - (startSec % baseStepSec);
            Instant modelTime = Instant.ofEpochSecond(startSec);

            // 设定回溯终点：过去 100 小时
            long stopSec = currentSec - (100 * 3600);
            stopSec = stopSec - (stopSec % baseStepSec);
            Instant stopTime = Instant.ofEpochSecond(stopSec);

            // 预先确保沙盘里存在 0 (WAITING) 的空洞占位 (注意传参顺序：过去 -> 未来)
            ensureZerosExistInContext(ctx, stopTime, modelTime);

            // 2. 核心大招：反向遍历！(modelTime 不断减去 baseStepSec)
            while (!modelTime.isBefore(stopTime)) {

                int inputStatus = checkInputsStatus(ctx, modelTime);

                // ----------------------------------------------------------------
                // 第二路：发现实测替换态 (2) -> 只要作为输入，强制重算！
                // ----------------------------------------------------------------
                if (inputStatus == 2) {
                    buildAndSubmitTask(ctx, modelTime, false);
                    changeInputStates2To1ForCurrentCursorOnly(ctx, modelTime);

                    break; // ✅ 发车即退，等待真实任务完成后的广播唤醒！
                }
                // ----------------------------------------------------------------
                // 第一路：发现数据齐全 (1) 且输出存在空洞 (0) -> 贪婪补漏计算！
                // ----------------------------------------------------------------
                else if (inputStatus == 1 && hasAnyOutputHole(ctx, modelTime)) {
                    buildAndSubmitTask(ctx, modelTime, true);

                    break; // ✅ 发车即退，等待真实任务完成后的广播唤醒！
                }

                // 🎯 算法灵魂：时光倒流，一步步往回溯！
                modelTime = modelTime.minusSeconds(baseStepSec);
            }
        }
    }
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
    /**
     * 🎯 严格分块防线：只检查这一趟输出的【第一帧】！
     * 只要第一帧有数据，说明整个块已经被上一个任务覆盖过了，游标静默滑过，直到遇到下一个真实的空洞起点！
     */
    private boolean isFirstOutputMomentAHole(ModelTriggerContext ctx, Instant modelTime) {
        for (Parameter p : ctx.getOutputParams()) {
            TimeAxis tAxis = p.getTimeAxis();
            if (tAxis == null) continue;

            long resSec = getAxisResolutionInSeconds(tAxis);
            int offset = (p.getoTimeStep() != null) ? p.getoTimeStep() : 0;
            Instant firstOutT = modelTime.plusSeconds(offset * resSec);

            for (Feature f : p.getFeatureList()) {
                TSState state = ctx.getLocalState(f.getId(), firstOutT);
                if (state == null || state.hasHoles()) return true;
            }
        }
        return false;
    }

    /**
     * 🎯 终极防线：只要这趟发车的输出窗口里，包含任何一个空洞，就说明它能推进边界，必须发车！
     * （多余的重叠帧会被底层存储的掩膜拦截，绝对安全）
     */
    private boolean hasAnyOutputHole(ModelTriggerContext ctx, Instant modelTime) {
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
                    // 只要发现输出序列里有任何一帧是空的，立刻返回 true 触发任务
                    if (state == null || state.hasHoles()) return true;
                }
            }
        }
        return false;
    }
    /**
     * 🎯 精准清除法：只清除游标所在的第一帧，绝不能用循环去清除整个窗口
     */
    private void changeInputStates2To1ForCurrentCursorOnly(ModelTriggerContext ctx, Instant modelTime) {
        for (Parameter p : ctx.getInputParams()) {
            TimeAxis tAxis = p.getTimeAxis();
            if (tAxis == null) continue;
            long resSec = getAxisResolutionInSeconds(tAxis);
            int offset = (p.getoTimeStep() != null) ? p.getoTimeStep() : 0;

            // 精确算出游标对应起点的这一帧时间
            Instant firstReqT = modelTime.plusSeconds(offset * resSec);

            for (Feature f : p.getFeatureList()) {
                TSState state = ctx.getLocalState(f.getId(), firstReqT);
                if (state != null && state.hasReplacedData()) {
                    state.getReplacedMask().clear();
                    state.getReadyMask().set(0, calculateFrameSize(p));
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

    // ===================================================================================
    // 🌍 [上帝视角] 打印真正的全局时间轴数据状态大盘 (跟随物理时钟推进)
    // ===================================================================================
    // 🎯 新增 synchronized 关键字，防止多线程异步广播时抢占控制台导致打印顺序错乱
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