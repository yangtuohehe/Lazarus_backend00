package com.example.lazarus_backend00.component.orchestration;

import com.example.lazarus_backend00.component.clock.VirtualTimeTickEvent;
import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.domain.axis.Feature;
import com.example.lazarus_backend00.domain.data.DataState;
import com.example.lazarus_backend00.domain.data.TSState;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.domain.data.TSShellFactory;
import com.example.lazarus_backend00.service.ModelOrchestratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
// ⚠️ 注意：这里已经删除了 @Component，完全交由 ModelTriggerConfig 来 @Bean 实例化
public class ModelEventTrigger {

    private final ModelOrchestratorService orchestratorService;
    private final AtomicLong taskIdGenerator = new AtomicLong(1000);

    // 位图状态表：FeatureId -> (TimeStep -> TSState)
    private final Map<Integer, Map<Instant, TSState>> globalStateTable = new ConcurrentHashMap<>();
    private final Map<Integer, ModelTriggerContext> registry = new ConcurrentHashMap<>();

    // ⚠️ 注意：这里去掉了 = Instant.now() 的就地初始化
    private Instant currentPhysicalTime;

    // 🔥 完美对齐的构造函数：接收服务和配置文件中读取的初始时间
    public ModelEventTrigger(ModelOrchestratorService orchestratorService, Instant startTime) {
        this.orchestratorService = orchestratorService;
        this.currentPhysicalTime = startTime;
        log.info("🎯 [触发器] 已成功由配置类接管实例化！初始虚拟时间设定为: {}", this.currentPhysicalTime);
    }

    public void registerModel(int runtimeId, List<Parameter> params, Duration step, Duration window) {
        registry.put(runtimeId, new ModelTriggerContext(runtimeId, params, step, window));
    }

    public void unregisterModel(int runtimeId) {
        registry.remove(runtimeId);
    }

    @EventListener
    public void onVirtualTimeTick(VirtualTimeTickEvent event) {
        this.currentPhysicalTime = event.getVirtualTime();
        evaluateAndDispatchTasks(false);
    }

    /**
     * 极速状态合并 (直接操作底层位图，无锁高并发安全)
     */
    @EventListener
    public void onDataStateUpdate(DataStateUpdateEvent event) {
        for (TSState incoming : event.getTsStates()) {
            globalStateTable.computeIfAbsent(incoming.getFeatureId(), k -> new ConcurrentHashMap<>())
                    .compute(incoming.getTOrigin(), (k, existing) -> {
                        if (existing == null) {
                            return new TSState(incoming);
                        }
                        existing.mergeState(incoming);
                        return existing;
                    });
        }
        evaluateAndDispatchTasks(event.isReplacedCorrection());
    }

    private void evaluateAndDispatchTasks(boolean isReplacedFlow) {
        Instant startScan = currentPhysicalTime.minus(Duration.ofHours(24));

        for (ModelTriggerContext ctx : registry.values()) {
            Duration step = ctx.getStep();

            for (Instant t = startScan; !t.isAfter(currentPhysicalTime); t = t.plus(step)) {

                if (!checkInputsReady(ctx, t)) continue;
                if (!checkTimeValidity(ctx, t)) continue;

                if (checkOutputsNeedCalc(ctx, t, isReplacedFlow)) {
                    buildAndSubmitTask(ctx, t, isReplacedFlow);
                }
            }
        }
    }

    private void buildAndSubmitTask(ModelTriggerContext ctx, Instant t, boolean isReplacedFlow) {
        // 1. 组装输入外壳
        List<ExecutableTask.TaskPort> inputPorts = new ArrayList<>();
        for (Parameter p : ctx.getInputParams()) {
            List<TSShell> shells = new ArrayList<>();
            for (Feature f : p.getFeatureList()) {
                shells.add(TSShellFactory.createFromParameter(f.getId(), t, p));
            }
            inputPorts.add(new ExecutableTask.TaskPort(p.getTensorOrder(), shells, null));
        }

        // 2. 组装输出外壳与位图掩码
        List<ExecutableTask.TaskPort> outputPorts = new ArrayList<>();
        for (Parameter p : ctx.getOutputParams()) {
            List<TSShell> shells = new ArrayList<>();
            List<TSState> targetStates = new ArrayList<>();

            for (Feature f : p.getFeatureList()) {
                shells.add(TSShellFactory.createFromParameter(f.getId(), t, p));

                // 获取当前时刻真实存在的状态掩码
                Map<Instant, TSState> timeMap = globalStateTable.get(f.getId());
                TSState currentState = (timeMap != null) ? timeMap.get(t) : null;

                if (currentState == null) {
                    // 如果底图全空，生成一个全空白(WAITING)的画板传下去
                    currentState = TSShellFactory.createTSStateFromParameter(f.getId(), t, p, DataState.WAITING);
                }
                // 深度拷贝状态传入任务，防止并发推演时原状态被篡改
                targetStates.add(new TSState(currentState));
            }
            outputPorts.add(new ExecutableTask.TaskPort(p.getTensorOrder(), shells, targetStates));
        }

        // 3. 真实下单
        ExecutableTask task = new ExecutableTask(
                taskIdGenerator.getAndIncrement(), ctx.getContainerId(), isReplacedFlow, inputPorts, outputPorts);
        orchestratorService.dispatchTask(task);
    }

    private boolean checkInputsReady(ModelTriggerContext ctx, Instant baseTime) {
        for (Parameter p : ctx.getInputParams()) {
            for (Feature f : p.getFeatureList()) {
                // 1. 获取全局缓存中的“大图”
                Map<Instant, TSState> timeMap = globalStateTable.get(f.getId());
                if (timeMap == null) return false;
                TSState globalState = timeMap.get(baseTime);
                if (globalState == null) return false;

                // 2. 基于 Parameter 定义模型真正需要的“局地外壳”
                TSShell requiredShell = TSShellFactory.createFromParameter(f.getId(), baseTime, p);

                // 3. 调用 GIS 工具类，从大图里精准裁剪/映射出局地状态图
                TSState localState = TSStateTopologyUtils.extractSubRegion(globalState, requiredShell);

                // 4. 检查裁剪出的这块自留地里，有没有没凑齐数据的“空洞”
                if (localState.hasHoles()) return false;
            }
        }
        return true;
    }

    private boolean checkTimeValidity(ModelTriggerContext ctx, Instant baseTime) {
        return !baseTime.isAfter(currentPhysicalTime);
    }

    private boolean checkOutputsNeedCalc(ModelTriggerContext ctx, Instant baseTime, boolean isReplacedFlow) {
        if (isReplacedFlow) return true;
        for (Parameter p : ctx.getOutputParams()) {
            for (Feature f : p.getFeatureList()) {
                Map<Instant, TSState> timeMap = globalStateTable.get(f.getId());
                if (timeMap == null) return true;

                TSState globalState = timeMap.get(baseTime);
                if (globalState == null) return true;

                // 同样进行空间映射与裁剪
                TSShell requiredShell = TSShellFactory.createFromParameter(f.getId(), baseTime, p);
                TSState localState = TSStateTopologyUtils.extractSubRegion(globalState, requiredShell);

                // 如果这片局地网格没算完，或者里面掺杂了实测替换数据，必须激活模型
                if (localState.hasHoles() || localState.hasReplacedData()) return true;
            }
        }
        return false;
    }
}