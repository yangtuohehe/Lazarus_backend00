package com.example.lazarus_backend00.component.orchestration;

import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.domain.data.TSState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.time.Instant;

/**
 * 结构化的模型触发契约与【模型自治状态机】
 */
public class ModelTriggerContext {

    private final int containerId;
    private final List<Parameter> inputParams;
    private final List<Parameter> outputParams;

    // 🎯 核心改变：模型维护自己独享的数据状态列表 (FeatureID -> (时刻 -> 状态包))
    private final Map<Integer, Map<Instant, TSState>> localStateTable = new ConcurrentHashMap<>();

    public ModelTriggerContext(int containerId, List<Parameter> params) {
        this.containerId = containerId;

        this.inputParams = params.stream()
                .filter(p -> "INPUT".equalsIgnoreCase(p.getIoType()))
                .sorted((p1, p2) -> Integer.compare(p1.getTensorOrder(), p2.getTensorOrder()))
                .collect(Collectors.toList());

        this.outputParams = params.stream()
                .filter(p -> "OUTPUT".equalsIgnoreCase(p.getIoType()))
                .sorted((p1, p2) -> Integer.compare(p1.getTensorOrder(), p2.getTensorOrder()))
                .collect(Collectors.toList());
    }

    // 接收全局路由过来的状态，深拷贝到自己的沙盘中
    public void updateLocalState(TSState incoming) {
        localStateTable.computeIfAbsent(incoming.getFeatureId(), k -> new ConcurrentHashMap<>())
                .put(incoming.getTOrigin(), new TSState(incoming));
    }

    public TSState getLocalState(int featureId, Instant time) {
        Map<Instant, TSState> featureMap = localStateTable.get(featureId);
        return featureMap != null ? featureMap.get(time) : null;
    }

    // 将缺少的坑位提前用 WAITING 占位
    public void ensureStateExists(int featureId, Instant time, Parameter p) {
        localStateTable.computeIfAbsent(featureId, k -> new ConcurrentHashMap<>())
                .putIfAbsent(time, com.example.lazarus_backend00.domain.data.TSShellFactory.createTSStateFromParameter(
                        featureId, time, p, com.example.lazarus_backend00.domain.data.DataState.WAITING));
    }

    public int getContainerId() { return containerId; }
    public List<Parameter> getInputParams() { return inputParams; }
    public List<Parameter> getOutputParams() { return outputParams; }
}