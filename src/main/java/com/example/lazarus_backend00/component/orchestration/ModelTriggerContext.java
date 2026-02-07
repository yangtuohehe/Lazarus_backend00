package com.example.lazarus_backend00.component.orchestration;

import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.domain.axis.Feature; // 假设 Feature 类在此包
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.domain.data.TSShellFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 模型触发状态上下文 (ModelTriggerContext) - 层级任务适配版
 * 职责：
 * 1. 维护特定模型的水位线 (Watermark)。
 * 2. 运行 UDCR (倒序搜索) 算法判断是否触发计算。
 * 3. 构造层级化的 ExecutableTask (TaskPort -> List<TSShell>)。
 * Access: Package-Private
 */
class ModelTriggerContext {

    // ================== 元数据 ==================
    private final int modelId;
    private final List<Parameter> inputParameters;
    private final List<Parameter> outputParameters;
    private final Duration timeStep;

    // 缓存所有输入特征 ID (用于 checkInputIntegrity 快速查表)
    // 注意：一个 Parameter 可能包含多个 Feature，这里存储平铺后的所有 ID
    private final List<Integer> inputFeatureIds;

    // ================== 动态状态 ==================
    private Instant modelLastComputedTime;

    // 内存倒排索引：FeatureID -> 该特征数据的最新时间水位
    private final Map<Integer, Instant> featureLatestTimeMap = new HashMap<>();

    // 线程安全的 ID 生成器
    private static final AtomicLong taskIdGenerator = new AtomicLong(0);

    /**
     * 构造函数
     */
    public ModelTriggerContext(int modelId,
                               List<Parameter> allParameters,
                               Duration timeStep,
                               Instant initialTime) {
        this.modelId = modelId;
        this.timeStep = timeStep;
        this.modelLastComputedTime = initialTime.minus(timeStep);

        this.inputParameters = new ArrayList<>();
        this.outputParameters = new ArrayList<>();
        this.inputFeatureIds = new ArrayList<>();

        // 1. 分类参数并初始化水位
        for (Parameter p : allParameters) {
            if ("INPUT".equalsIgnoreCase(p.getIoType())) {
                this.inputParameters.add(p);

                // 🔥 修正：必须监听 Parameter 中的【所有】特征，而不仅仅是第一个
                if (p.getFeatureList() != null) {
                    for (Feature f : p.getFeatureList()) {
                        this.inputFeatureIds.add(f.getId());
                        // 初始化水位为最小时间
                        this.featureLatestTimeMap.put(f.getId(), Instant.MIN);
                    }
                }

            } else if ("OUTPUT".equalsIgnoreCase(p.getIoType())) {
                this.outputParameters.add(p);
            }
        }
    }

    public int getModelId() { return modelId; }

    public List<Integer> getInputFeatureIds() {
        return Collections.unmodifiableList(inputFeatureIds);
    }

    /**
     * 处理数据更新信号
     */
    public synchronized List<ExecutableTask> processUpdate(TSShell dataShell, Instant globalTNow) {
        int fid = dataShell.getFeatureId();
        Instant currentMax = featureLatestTimeMap.getOrDefault(fid, Instant.MIN);

        // 1. 更新特征水位 (Watermark Advancement)
        if (dataShell.getTEnd().isAfter(currentMax)) {
            featureLatestTimeMap.put(fid, dataShell.getTEnd());
        }

        // 2. 仿真时间回退处理
        if (globalTNow.isBefore(modelLastComputedTime)) {
            System.out.println("🔄 [Context] 时间回退，重置模型 " + modelId + " 状态");
            this.modelLastComputedTime = globalTNow.minus(timeStep);
        }

        // 3. 执行核心反向搜索算法
        return runUDCR(dataShell, globalTNow);
    }

    /**
     * UDCR (Upstream Data Coverage Reverse-search) 算法
     */
    private List<ExecutableTask> runUDCR(TSShell triggerSourceShell, Instant tNow) {
        List<Instant> validTimePoints = new ArrayList<>();

        Instant p = alignTime(tNow, timeStep);
        Instant searchLimit = modelLastComputedTime;

        while (p.isAfter(searchLimit)) {
            // A. 完整性检查 (Integrity Check)
            // 必须所有输入特征的数据都覆盖到了 p+step，才能计算
            if (!checkInputIntegrity(p)) {
                p = p.minus(timeStep);
                continue;
            }

            // B. 必要性检查 (Necessity Check)
            if (checkValueNecessity(p, triggerSourceShell)) {
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

    // ================== 检查逻辑 ==================

    private boolean checkInputIntegrity(Instant p) {
        Instant requiredEnd = p.plus(timeStep);
        // 遍历所有被监听的 Input Feature ID
        for (Integer inputId : inputFeatureIds) {
            Instant latest = featureLatestTimeMap.get(inputId);
            // 如果某个特征没被初始化(null)或者水位不够
            if (latest == null || latest.isBefore(requiredEnd)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkValueNecessity(Instant p, TSShell triggerShell) {
        if (p.isAfter(modelLastComputedTime)) return true;
        Instant pEnd = p.plus(timeStep);
        return !triggerShell.getTOrigin().isAfter(pEnd) && !triggerShell.getTEnd().isBefore(p);
    }

    // ================== 任务生成 (核心重构) ==================

    private ExecutableTask createTask(Instant executeTime) {
        long taskId = taskIdGenerator.incrementAndGet();

        // 1. 构建输入端口列表 (List<TaskPort>)
        List<ExecutableTask.TaskPort> inputPorts = new ArrayList<>();

        for (Parameter param : inputParameters) {
            // 这里的 param 代表模型的一个输入张量 (e.g. Temperature + Humidity)
            List<TSShell> shellsForThisPort = new ArrayList<>();

            if (param.getFeatureList() != null) {
                // 遍历该张量所需的每个特征，生成独立的 Shell
                for (Feature f : param.getFeatureList()) {
                    // 使用 Factory 生成单特征外壳
                    TSShell shell = TSShellFactory.createFromParameter(f.getId(), executeTime, param);
                    shellsForThisPort.add(shell);
                }
            }

            // 打包成一个端口 (Port)
            inputPorts.add(new ExecutableTask.TaskPort(param.getTensorOrder(), shellsForThisPort));
        }

        // 2. 构建输出端口列表 (List<TaskPort>)
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

        // 3. 返回新的层级化任务对象
        return new ExecutableTask(
                taskId,
                this.modelId,
                inputPorts,  // List<TaskPort>
                outputPorts  // List<TaskPort>
        );
    }

    // ================== 工具方法 ==================

    private Instant alignTime(Instant time, Duration step) {
        long stepMillis = step.toMillis();
        if (stepMillis == 0) return time;
        long epochMillis = time.toEpochMilli();
        return Instant.ofEpochMilli((epochMillis / stepMillis) * stepMillis);
    }
}