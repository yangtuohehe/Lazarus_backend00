//package com.example.lazarus_backend00.component.orchestration;
//
//import com.example.lazarus_backend00.component.clock.VirtualTimeTickEvent;
//import com.example.lazarus_backend00.component.container.Parameter;
//import com.example.lazarus_backend00.domain.axis.Feature;
//import com.example.lazarus_backend00.domain.data.DataState;
//import com.example.lazarus_backend00.domain.data.TSState;
//import com.example.lazarus_backend00.domain.data.TSShell;
//import com.example.lazarus_backend00.domain.data.TSShellFactory;
//import com.example.lazarus_backend00.service.ModelOrchestratorService;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.context.event.EventListener;
//
//import java.time.Duration;
//import java.time.Instant;
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.atomic.AtomicLong;
//
//@Slf4j
//// ⚠️ 注意：这里已经删除了 @Component，完全交由 ModelTriggerConfig 来 @Bean 实例化
//public class ModelEventTrigger {
//
//    private final ModelOrchestratorService orchestratorService;
//    private final AtomicLong taskIdGenerator = new AtomicLong(1000);
//
//    // 位图状态表：FeatureId -> (TimeStep -> TSState)
//    private final Map<Integer, Map<Instant, TSState>> globalStateTable = new ConcurrentHashMap<>();
//    private final Map<Integer, ModelTriggerContext> registry = new ConcurrentHashMap<>();
//
//    // ⚠️ 注意：这里去掉了 = Instant.now() 的就地初始化
//    private Instant currentPhysicalTime;
//
//    // 🔥 完美对齐的构造函数：接收服务和配置文件中读取的初始时间
//    public ModelEventTrigger(ModelOrchestratorService orchestratorService, Instant startTime) {
//        this.orchestratorService = orchestratorService;
//        this.currentPhysicalTime = startTime;
//        log.info("🎯 [触发器] 已成功由配置类接管实例化！初始虚拟时间设定为: {}", this.currentPhysicalTime);
//    }
//
//    public void registerModel(int runtimeId, List<Parameter> params, Duration step, Duration window) {
//        registry.put(runtimeId, new ModelTriggerContext(runtimeId, params, step, window));
//    }
//
//    public void unregisterModel(int runtimeId) {
//        registry.remove(runtimeId);
//    }
//
//    @EventListener
//    public void onVirtualTimeTick(VirtualTimeTickEvent event) {
//        this.currentPhysicalTime = event.getVirtualTime();
//        evaluateAndDispatchTasks(false);
//    }
//
//    /**
//     * 极速状态合并 (直接操作底层位图，无锁高并发安全)
//     */
//    @EventListener
//    public void onDataStateUpdate(DataStateUpdateEvent event) {
//        for (TSState incoming : event.getTsStates()) {
//            globalStateTable.computeIfAbsent(incoming.getFeatureId(), k -> new ConcurrentHashMap<>())
//                    .compute(incoming.getTOrigin(), (k, existing) -> {
//                        if (existing == null) {
//                            return new TSState(incoming);
//                        }
//                        existing.mergeState(incoming);
//                        return existing;
//                    });
//        }
//        evaluateAndDispatchTasks(event.isReplacedCorrection());
//    }
//
//    private void evaluateAndDispatchTasks(boolean isReplacedFlow) {
//        Instant startScan = currentPhysicalTime.minus(Duration.ofHours(24));
//
//        for (ModelTriggerContext ctx : registry.values()) {
//            Duration step = ctx.getStep();
//
//            for (Instant t = startScan; !t.isAfter(currentPhysicalTime); t = t.plus(step)) {
//
//                if (!checkInputsReady(ctx, t)) continue;
//                if (!checkTimeValidity(ctx, t)) continue;
//
//                if (checkOutputsNeedCalc(ctx, t, isReplacedFlow)) {
//                    buildAndSubmitTask(ctx, t, isReplacedFlow);
//                }
//            }
//        }
//    }
//
//    private void buildAndSubmitTask(ModelTriggerContext ctx, Instant t, boolean isReplacedFlow) {
//        // 1. 组装输入外壳
//        List<ExecutableTask.TaskPort> inputPorts = new ArrayList<>();
//        for (Parameter p : ctx.getInputParams()) {
//            List<TSShell> shells = new ArrayList<>();
//            for (Feature f : p.getFeatureList()) {
//                shells.add(TSShellFactory.createFromParameter(f.getId(), t, p));
//            }
//            inputPorts.add(new ExecutableTask.TaskPort(p.getTensorOrder(), shells, null));
//        }
//
//        // 2. 组装输出外壳与位图掩码
//        List<ExecutableTask.TaskPort> outputPorts = new ArrayList<>();
//        for (Parameter p : ctx.getOutputParams()) {
//            List<TSShell> shells = new ArrayList<>();
//            List<TSState> targetStates = new ArrayList<>();
//
//            for (Feature f : p.getFeatureList()) {
//                shells.add(TSShellFactory.createFromParameter(f.getId(), t, p));
//
//                // 获取当前时刻真实存在的状态掩码
//                Map<Instant, TSState> timeMap = globalStateTable.get(f.getId());
//                TSState currentState = (timeMap != null) ? timeMap.get(t) : null;
//
//                if (currentState == null) {
//                    // 如果底图全空，生成一个全空白(WAITING)的画板传下去
//                    currentState = TSShellFactory.createTSStateFromParameter(f.getId(), t, p, DataState.WAITING);
//                }
//                // 深度拷贝状态传入任务，防止并发推演时原状态被篡改
//                targetStates.add(new TSState(currentState));
//            }
//            outputPorts.add(new ExecutableTask.TaskPort(p.getTensorOrder(), shells, targetStates));
//        }
//
//        // 3. 真实下单
//        ExecutableTask task = new ExecutableTask(
//                taskIdGenerator.getAndIncrement(), ctx.getContainerId(), isReplacedFlow, inputPorts, outputPorts);
//        orchestratorService.dispatchTask(task);
//    }
//
//    private boolean checkInputsReady(ModelTriggerContext ctx, Instant baseTime) {
//        for (Parameter p : ctx.getInputParams()) {
//            for (Feature f : p.getFeatureList()) {
//                // 1. 获取全局缓存中的“大图”
//                Map<Instant, TSState> timeMap = globalStateTable.get(f.getId());
//                if (timeMap == null) return false;
//                TSState globalState = timeMap.get(baseTime);
//                if (globalState == null) return false;
//
//                // 2. 基于 Parameter 定义模型真正需要的“局地外壳”
//                TSShell requiredShell = TSShellFactory.createFromParameter(f.getId(), baseTime, p);
//
//                // 3. 调用 GIS 工具类，从大图里精准裁剪/映射出局地状态图
//                TSState localState = TSStateTopologyUtils.extractSubRegion(globalState, requiredShell);
//
//                // 4. 检查裁剪出的这块自留地里，有没有没凑齐数据的“空洞”
//                if (localState.hasHoles()) return false;
//            }
//        }
//        return true;
//    }
//
//    private boolean checkTimeValidity(ModelTriggerContext ctx, Instant baseTime) {
//        return !baseTime.isAfter(currentPhysicalTime);
//    }
//
//    private boolean checkOutputsNeedCalc(ModelTriggerContext ctx, Instant baseTime, boolean isReplacedFlow) {
//        if (isReplacedFlow) return true;
//        for (Parameter p : ctx.getOutputParams()) {
//            for (Feature f : p.getFeatureList()) {
//                Map<Instant, TSState> timeMap = globalStateTable.get(f.getId());
//                if (timeMap == null) return true;
//
//                TSState globalState = timeMap.get(baseTime);
//                if (globalState == null) return true;
//
//                // 同样进行空间映射与裁剪
//                TSShell requiredShell = TSShellFactory.createFromParameter(f.getId(), baseTime, p);
//                TSState localState = TSStateTopologyUtils.extractSubRegion(globalState, requiredShell);
//
//                // 如果这片局地网格没算完，或者里面掺杂了实测替换数据，必须激活模型
//                if (localState.hasHoles() || localState.hasReplacedData()) return true;
//            }
//        }
//        return false;
//    }
//}
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
import java.util.stream.Collectors;

@Slf4j
public class ModelEventTrigger {

    private final ModelOrchestratorService orchestratorService;
    private final AtomicLong taskIdGenerator = new AtomicLong(1000);

    /**
     * 全局状态位图大盘：FeatureID -> (时刻 -> 状态包)
     */
    private final Map<Integer, Map<Instant, TSState>> globalStateTable = new ConcurrentHashMap<>();

    /**
     * 模型运行上下文注册表：RuntimeID -> 触发配置
     */
    private final Map<Integer, ModelTriggerContext> registry = new ConcurrentHashMap<>();

    /**
     * 当前系统的虚拟物理时间
     */
    private Instant currentPhysicalTime;

    public ModelEventTrigger(ModelOrchestratorService orchestratorService, Instant startTime) {
        this.orchestratorService = orchestratorService;
        this.currentPhysicalTime = startTime;
        log.info("🎯 [Trigger] 触发器初始化完成，起始虚拟时间: {}", this.currentPhysicalTime);
    }

    public void registerModel(int runtimeId, List<Parameter> params, Duration step, Duration window) {
        registry.put(runtimeId, new ModelTriggerContext(runtimeId, params, step, window));
        log.info("📝 [Trigger] 模型注册成功，RuntimeID: {}, 步长: {}", runtimeId, step);
    }

    public void unregisterModel(int runtimeId) {
        registry.remove(runtimeId);
        log.info("🗑️ [Trigger] Unregistered Model RuntimeID: {}", runtimeId);
    }
    @EventListener
    public void onVirtualTimeTick(VirtualTimeTickEvent event) {
        this.currentPhysicalTime = event.getVirtualTime();
        // 虚拟时钟跳动，触发全量评估
        evaluateAndDispatchTasks(false);
    }

    /**
     * 接收来自数据子系统或其他模型计算完成的状态更新包
     */
    @EventListener
    public void onDataStateUpdate(DataStateUpdateEvent event) {
        for (TSState incoming : event.getTsStates()) {
            globalStateTable.computeIfAbsent(incoming.getFeatureId(), k -> new ConcurrentHashMap<>())
                    .put(incoming.getTOrigin(), new TSState(incoming));
        }
        // 数据状态更新，触发评估。如果是重算流（Correction），则强制触发
        evaluateAndDispatchTasks(event.isReplacedCorrection());
    }

    /**
     * 核心调度引擎：对应 Python 中的 evaluate_and_run_models
     */
    private void evaluateAndDispatchTasks(boolean isReplacedFlow) {
        if (registry.isEmpty()) return;

        // 默认扫描窗口：过去 24 小时到当前时刻
        Instant startScan = currentPhysicalTime.minus(Duration.ofHours(24));

        for (ModelTriggerContext ctx : registry.values()) {
            Duration step = ctx.getStep();

            // 遍历时间轴上的每一个步长点
            for (Instant t = startScan; !t.isAfter(currentPhysicalTime); t = t.plus(step)) {

                // 🔥 [打印监控]：显示该时刻大盘中的所有可用特征
                List<Integer> readyFeatures = getReadyFeaturesAtInstant(t);
                log.info("📊 [State Matrix] Time: {} | Available Features: {}", t, readyFeatures);

                // 1. 输入齐备性检查：对应 Python inputs_ready
                // 必须检查 Parameter 时间轴定义的每一个离散偏移点
                if (!checkInputsReadyStrict(ctx, t)) {
                    continue;
                }

                // 2. 超前阻断与计算必要性检查：对应 Python exceeds_current_time & needs_calc
                if (checkCalculationEligibility(ctx, t, isReplacedFlow)) {
                    // 3. 执行：对应 Python 的 lineage 注册与 set_twin_state(0)
                    buildAndSubmitTask(ctx, t, isReplacedFlow);
                    log.info("🚀 [Dispatch] 条件满足，任务已下发! Model: {}, 目标时刻: {}", ctx.getContainerId(), t);
                }
            }
        }
    }

    /**
     * 严格检查输入：遍历输入参数定义的每一个时间步
     */
    private boolean checkInputsReadyStrict(ModelTriggerContext ctx, Instant baseTime) {
        for (Parameter p : ctx.getInputParams()) {
            // 🚨 保护 1：如果该参数没有时间轴，说明它是静态数据，直接放行，跳过时间维度的校验
            TimeAxis tAxis = p.getTimeAxis();
            if (tAxis == null || tAxis.getResolution() == null) {
                continue;
            }

            int count = tAxis.getCount();
            double res = tAxis.getResolution();
            String unit = tAxis.getUnit();

            // 🚨 保护 2：时间单位智能转换 (将 JSON 里的 "hour" 转换为真实的秒数)
            long resSeconds = (long) (unit != null && (unit.equalsIgnoreCase("hour") || unit.equalsIgnoreCase("hours") || unit.equalsIgnoreCase("h"))
                    ? res * 3600 : res);

            for (Feature f : p.getFeatureList()) {
                // 检查每一个偏移点：t + 0, t + 1*res, t + 2*res ...
                for (int i = 0; i < count; i++) {
                    Instant reqT = baseTime.plus(Duration.ofSeconds(i * resSeconds));
                    Map<Instant, TSState> timeMap = globalStateTable.get(f.getId());

                    if (timeMap == null || !timeMap.containsKey(reqT)) {
                        log.debug("   ↳ [Input Gap] FeatureID: {} 缺失时刻: {}", f.getId(), reqT);
                        return false;
                    }
                    // 注意：这里默认认为在大盘里的数据都是 READY 或 REPLACED，只要存在即视为“有输入数据”
                }
            }
        }
        return true;
    }

    /**
     * 严格检查输出资格：包含超前阻断和空洞/污染检测
     */
    private boolean checkCalculationEligibility(ModelTriggerContext ctx, Instant baseTime, boolean isReplacedFlow) {
        // 如果是强制重算流，直接允许
        if (isReplacedFlow) return true;

        boolean needsCalc = false;

        for (Parameter p : ctx.getOutputParams()) {
            // 🚨 保护 1：如果没有时间轴，跳过
            TimeAxis tAxis = p.getTimeAxis();
            if (tAxis == null || tAxis.getResolution() == null) {
                continue;
            }

            int count = tAxis.getCount();
            double res = tAxis.getResolution();
            String unit = tAxis.getUnit();

            // 🚨 保护 2：时间单位智能转换
            long resSeconds = (long) (unit != null && (unit.equalsIgnoreCase("hour") || unit.equalsIgnoreCase("hours") || unit.equalsIgnoreCase("h"))
                    ? res * 3600 : res);

            for (Feature f : p.getFeatureList()) {
                for (int i = 0; i < count; i++) {
                    Instant outT = baseTime.plus(Duration.ofSeconds(i * resSeconds));

                    // ✅ 【绝对红线】：超前计算阻断器 (如果输出超过物理时间，绝对不执行！)
                    if (outT.isAfter(currentPhysicalTime)) {
                        log.debug("   ↳ [Block] 输出时刻 {} 超前于系统时间 {}, 阻断计算", outT, currentPhysicalTime);
                        return false;
                    }

                    // ✅ 【空洞/污染检查】
                    Map<Instant, TSState> timeMap = globalStateTable.get(f.getId());
                    if (timeMap == null || !timeMap.containsKey(outT)) {
                        needsCalc = true; // 缺失点，需要计算补齐
                    } else {
                        // 只要有一个点是 WAITING（空洞）或 REPLACED（被污染需修正），就触发重算
                        TSState state = timeMap.get(outT);
                        if (state.hasHoles() || state.hasReplacedData()) {
                            needsCalc = true;
                        }
                    }
                }
            }
        }
        return needsCalc;
    }

    /**
     * 辅助方法：获取特定时刻大盘中已存在的特征 ID 列表
     */
    private List<Integer> getReadyFeaturesAtInstant(Instant t) {
        List<Integer> list = new ArrayList<>();
        for (Map.Entry<Integer, Map<Instant, TSState>> entry : globalStateTable.entrySet()) {
            if (entry.getValue().containsKey(t)) {
                list.add(entry.getKey());
            }
        }
        Collections.sort(list);
        return list;
    }

    /**
     * 组装任务并提交给编排服务
     */
    private void buildAndSubmitTask(ModelTriggerContext ctx, Instant t, boolean isReplacedFlow) {
        // 1. 组装输入端口
        List<ExecutableTask.TaskPort> inputPorts = new ArrayList<>();
        for (Parameter p : ctx.getInputParams()) {
            List<TSShell> shells = new ArrayList<>();
            for (Feature f : p.getFeatureList()) {
                shells.add(TSShellFactory.createFromParameter(f.getId(), t, p));
            }
            inputPorts.add(new ExecutableTask.TaskPort(p.getTensorOrder(), shells, null));
        }

        // 2. 组装输出端口及期望状态掩码
        List<ExecutableTask.TaskPort> outputPorts = new ArrayList<>();
        for (Parameter p : ctx.getOutputParams()) {
            List<TSShell> shells = new ArrayList<>();
            List<TSState> targetStates = new ArrayList<>();

            for (Feature f : p.getFeatureList()) {
                shells.add(TSShellFactory.createFromParameter(f.getId(), t, p));

                Map<Instant, TSState> timeMap = globalStateTable.get(f.getId());
                TSState currentState = (timeMap != null) ? timeMap.get(t) : null;

                if (currentState == null) {
                    currentState = TSShellFactory.createTSStateFromParameter(f.getId(), t, p, DataState.WAITING);
                }
                targetStates.add(new TSState(currentState));
            }
            outputPorts.add(new ExecutableTask.TaskPort(p.getTensorOrder(), shells, targetStates));
        }

        // 3. 生成全局唯一任务并下发
        long taskId = taskIdGenerator.getAndIncrement();
        ExecutableTask task = new ExecutableTask(
                taskId,
                ctx.getContainerId(),
                isReplacedFlow,
                inputPorts,
                outputPorts
        );

        orchestratorService.dispatchTask(task);
    }
}