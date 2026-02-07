//package com.example.lazarus_backend00.component.orchestration;
//
//import com.example.lazarus_backend00.domain.axis.Axis;
//import com.example.lazarus_backend00.domain.axis.Feature;
//import com.example.lazarus_backend00.domain.axis.SpaceAxisX;
//import com.example.lazarus_backend00.domain.axis.SpaceAxisY;
//import com.example.lazarus_backend00.component.container.ModelContainer;
//import com.example.lazarus_backend00.component.container.Parameter;
//import org.locationtech.jts.geom.Geometry;
//
//import java.util.*;
//import java.util.stream.Collectors;
//
///**
// * 全局模型依赖图 (Model DAG)
// * 核心职责：利用 JTS 空间拓扑算法，自动推演模型间的 "内存零拷贝" 数据管道。
// */
//public class ModelGraph {
//
//    /**
//     * 图的核心存储结构：邻接表 (Adjacency List)
//     * Key: 源模型 ID (Source Model)
//     * Value: 该模型输出流向的目标边列表 (下游模型 ID + 传输的特征 ID)
//     */
//    private final Map<Integer, List<ModelEdge>> adjacencyList = new HashMap<>();
//
//    public Map<Integer, List<ModelEdge>> getAdjacencyList() {
//        return adjacencyList;
//    }
//
//    public Map<Integer, Set<Integer>> getInDegreeMap() {
//        return inDegreeMap;
//    }
//
//    /**
//     * 逆向查询表：Key 是目标模型 ID，Value 是它需要的所有前置模型 ID
//     * (用于调度器判断：模型B 的所有上游前置任务是否都已在内存中就绪)
//     */
//    private final Map<Integer, Set<Integer>> inDegreeMap = new HashMap<>();
//
//
//    // ================== 核心功能：自动构建依赖图 ==================
//
//    /**
//     * 根据容器池中的注册表，自动计算并建立图拓扑。
//     * @param containerRegistry ModelContainerPool 中的模型注册表
//     */
//    public void buildGraph(Map<Integer, ModelContainer> containerRegistry) {
//        adjacencyList.clear();
//        inDegreeMap.clear();
//
//        Collection<ModelContainer> allModels = containerRegistry.values();
//
//        // N^2 复杂度遍历所有模型对 (A, B)，判断 A 能否作为 B 的上游
//        for (ModelContainer modelA : allModels) {
//            for (ModelContainer modelB : allModels) {
//                if (modelA.getContainerId() == modelB.getContainerId()) continue;
//
//                // 将参数按 I/O 类型分类
//                List<Parameter> aOutputs = filterParameters(modelA.getParameterList(), "OUTPUT");
//                List<Parameter> bInputs = filterParameters(modelB.getParameterList(), "INPUT");
//
//                for (Parameter outA : aOutputs) {
//                    for (Parameter inB : bInputs) {
//                        // 🌟 核心算法：检查参数 A 与 B 是否耦合
//                        Set<Integer> matchedFeatures = checkCoupling(outA, inB);
//
//                        // 如果耦合成立，为每一个匹配的特征建立一条数据传输边
//                        for (int featureId : matchedFeatures) {
//                            addEdge(modelA.getContainerId(), modelB.getContainerId(), featureId);
//                        }
//                    }
//                }
//            }
//        }
//        System.out.println("✅ 模型 DAG 重建完成。共发现 " + countEdges() + " 条内存数据直传管道。");
//    }
//
//    // ================== JTS 拓扑校验算法 ==================
//
//    /**
//     * 检查 模型A的输出参数(outA) 是否能完全满足 模型B的输入需求(inB)
//     * @return 匹配的特征ID集合 (若为空则表示不耦合)
//     */
//    private Set<Integer> checkCoupling(Parameter outA, Parameter inB) {
//
//        // 1. 物理特征必须匹配 (取特征列表的交集)
//        Set<Integer> aFeatures = outA.getFeatureList().stream().map(Feature::getId).collect(Collectors.toSet());
//        Set<Integer> bFeatures = inB.getFeatureList().stream().map(Feature::getId).collect(Collectors.toSet());
//        aFeatures.retainAll(bFeatures); // 交集
//        if (aFeatures.isEmpty()) return Collections.emptySet();
//
//        // 2. 空间分辨率必须一致
//        if (!isResolutionMatch(outA.getAxisList(), inB.getAxisList())) {
//            return Collections.emptySet();
//        }
//
//        // 3. 🌟 JTS 空间拓扑校验 (降维打击级算法) 🌟
//        // 语义：A 的产出范围 必须 "完全包含" (Contains) B 的需求范围。
//        // 这行代码自动支持了：2D包含2D，3D包含3D，甚至 Polygon包含Point！
//        Geometry geomA = outA.getCoverageGeom();
//        Geometry geomB = inB.getCoverageGeom();
//
//        if (geomA != null && geomB != null && geomA.contains(geomB)) {
//            return aFeatures; // 耦合成立！返回匹配的特征
//        }
//
//        return Collections.emptySet();
//    }
//
//    /**
//     * 校验分辨率：提取 X 和 Y 轴的 Resolution 并比对
//     */
//    private boolean isResolutionMatch(List<Axis> axesA, List<Axis> axesB) {
//        Double resXA = getResolution(axesA, SpaceAxisX.class);
//        Double resXB = getResolution(axesB, SpaceAxisX.class);
//        Double resYA = getResolution(axesA, SpaceAxisY.class);
//        Double resYB = getResolution(axesB, SpaceAxisY.class);
//
//        return Objects.equals(resXA, resXB) && Objects.equals(resYA, resYB);
//    }
//
//    private <T extends Axis> Double getResolution(List<Axis> axes, Class<T> clazz) {
//        return axes.stream()
//                .filter(clazz::isInstance)
//                .map(Axis::getResolution)
//                .findFirst()
//                .orElse(null);
//    }
//
//    // ================== 图操作与查询 API ==================
//
//    private void addEdge(int fromModelId, int toModelId, int featureId) {
//        // 构建正向图
//        adjacencyList.computeIfAbsent(fromModelId, k -> new ArrayList<>())
//                .add(new ModelEdge(toModelId, featureId));
//
//        // 构建逆向依赖度图
//        inDegreeMap.computeIfAbsent(toModelId, k -> new HashSet<>())
//                .add(fromModelId);
//    }
//
//    /**
//     * 获取指定模型的“下游模型”列表。
//     * [用途]: 当 A 算完生成 TSDataBlock 后，调度器查这张表，直接把内存指针喂给 B 和 C。
//     */
//    public List<ModelEdge> getDownstreamModels(int modelId) {
//        return adjacencyList.getOrDefault(modelId, Collections.emptyList());
//    }
//
//    /** 获取指定模型依赖的“上游模型”列表 */
//    public Set<Integer> getUpstreamModelIds(int modelId) {
//        return inDegreeMap.getOrDefault(modelId, Collections.emptySet());
//    }
//
//    private List<Parameter> filterParameters(List<Parameter> allParams, String targetIoType) {
//        return allParams.stream()
//                .filter(p -> targetIoType.equalsIgnoreCase(p.getIoType()))
//                .collect(Collectors.toList());
//    }
//
//    private int countEdges() {
//        return adjacencyList.values().stream().mapToInt(List::size).sum();
//    }
//
//    // ================== 内部类：数据流管道定义 ==================
//
//    /**
//     * 模型依赖边 (DAG Edge)
//     * 代表 A 模型算出的某个特征数据，需要直接流向 targetModelId。
//     */
//    public static class ModelEdge {
//        public final int targetModelId;     // 数据流向的模型 ID
//        public final int transferFeatureId; // 传输的数据特征 (如: 101 温度)
//
//        public ModelEdge(int targetModelId, int transferFeatureId) {
//            this.targetModelId = targetModelId;
//            this.transferFeatureId = transferFeatureId;
//        }
//
//        @Override
//        public String toString() {
//            return "-> Model_" + targetModelId + " (Feature:" + transferFeatureId + ")";
//        }
//    }
//}