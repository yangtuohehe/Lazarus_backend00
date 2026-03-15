//package com.example.lazarus_backend00.component.orchestration;
//
//import com.example.lazarus_backend00.component.container.Parameter;
//import com.example.lazarus_backend00.domain.data.TSState;
//
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.stream.Collectors;
//import java.time.Instant;
//
///**
// * 结构化的模型触发契约与【模型自治状态机】
// */
//public class ModelTriggerContext {
//
//    private final int containerId;
//    private final List<Parameter> inputParams;
//    private final List<Parameter> outputParams;
//
//    // 🎯 核心改变：模型维护自己独享的数据状态列表 (FeatureID -> (时刻 -> 状态包))
//    private final Map<Integer, Map<Instant, TSState>> localStateTable = new ConcurrentHashMap<>();
//
//    public ModelTriggerContext(int containerId, List<Parameter> params) {
//        this.containerId = containerId;
//
//        this.inputParams = params.stream()
//                .filter(p -> "INPUT".equalsIgnoreCase(p.getIoType()))
//                .sorted((p1, p2) -> Integer.compare(p1.getTensorOrder(), p2.getTensorOrder()))
//                .collect(Collectors.toList());
//
//        this.outputParams = params.stream()
//                .filter(p -> "OUTPUT".equalsIgnoreCase(p.getIoType()))
//                .sorted((p1, p2) -> Integer.compare(p1.getTensorOrder(), p2.getTensorOrder()))
//                .collect(Collectors.toList());
//    }
//
//    // 接收全局路由过来的状态，深拷贝到自己的沙盘中
//    // 接收全局路由过来的状态，深拷贝到自己的沙盘中（带有防降级保护）
//    public void updateLocalState(TSState incoming) {
//        Map<Instant, TSState> featureMap = localStateTable.computeIfAbsent(incoming.getFeatureId(), k -> new ConcurrentHashMap<>());
//
//        featureMap.compute(incoming.getTOrigin(), (key, existingState) -> {
//            // 1. 如果本地还没坑位，直接存入
//            if (existingState == null) {
//                return new TSState(incoming);
//            }
//
//            // 2. 如果本地已经有了，做降级防御评估
//            boolean localIsHole = existingState.hasHoles();
//            boolean localIsReplaced = existingState.hasReplacedData();
//            boolean incomingIsHole = incoming.hasHoles();
//            boolean incomingIsReplaced = incoming.hasReplacedData();
//
//            // 🛑 防御一：本地是实测态(2)，绝不接受预测态(1)或空态(0)的覆盖
//            if (localIsReplaced && !incomingIsReplaced) {
//                return existingState;
//            }
//
//            // 🛑 防御二：本地是预测完的就绪态(1，无空洞)，绝不接受空态(0，有空洞)的覆盖
//            if (!localIsHole && incomingIsHole) {
//                return existingState;
//            }
//
//            // 🟢 允许升级或平替：0变1，1变2，或者状态相同刷新数据包
//            return new TSState(incoming);
//        });
//    }
////    public void updateLocalState(TSState incoming) {
////        localStateTable.computeIfAbsent(incoming.getFeatureId(), k -> new ConcurrentHashMap<>())
////                .put(incoming.getTOrigin(), new TSState(incoming));
////    }
//
//    public TSState getLocalState(int featureId, Instant time) {
//        Map<Instant, TSState> featureMap = localStateTable.get(featureId);
//        return featureMap != null ? featureMap.get(time) : null;
//    }
//
//    // 将缺少的坑位提前用 WAITING 占位
//    public void ensureStateExists(int featureId, Instant time, Parameter p) {
//        localStateTable.computeIfAbsent(featureId, k -> new ConcurrentHashMap<>())
//                .putIfAbsent(time, com.example.lazarus_backend00.domain.data.TSShellFactory.createTSStateFromParameter(
//                        featureId, time, p, com.example.lazarus_backend00.domain.data.DataState.WAITING));
//    }
//
//    public int getContainerId() { return containerId; }
//    public List<Parameter> getInputParams() { return inputParams; }
//    public List<Parameter> getOutputParams() { return outputParams; }
//}
package com.example.lazarus_backend00.component.orchestration;

import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.domain.data.TSState;

import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // 🎯 核心改变1：模型维护自己独享的数据状态列表 (FeatureID -> (时刻 -> 状态包))
    private final Map<Integer, Map<Instant, TSState>> localStateTable = new ConcurrentHashMap<>();

    // 🎯 核心改变2：新增【计算任务历史记录表】，用于正向/逆向双路查重，防止发车碰撞
    private final Set<Instant> taskRecordTable = ConcurrentHashMap.newKeySet();

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

    // ================= 任务记录表操作 =================
    public void addTaskRecord(Instant time) {
        taskRecordTable.add(time);
    }

    public void removeTaskRecord(Instant time) {
        taskRecordTable.remove(time);
    }

    public boolean hasTaskRecord(Instant time) {
        return taskRecordTable.contains(time);
    }

    public int getContainerId() { return containerId; }
    public List<Parameter> getInputParams() { return inputParams; }
    public List<Parameter> getOutputParams() { return outputParams; }
}