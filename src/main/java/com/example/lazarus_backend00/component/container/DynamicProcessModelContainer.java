//package com.example.lazarus_backend00.model.container;
//
//import com.example.lazarus_backend00.domain.data.TSDataBlock;
//
//import java.util.List;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.concurrent.atomic.AtomicReference;
//
///**
// * 通用动态模型容器实现类
// * 职责：管理底层推理引擎 (如 ONNX Runtime) 的 Session，
// * 并将 Java 层的 TSDataBlock 转换为底层引擎所需的内存张量。
// */
//public class DynamicProcessModelContainer implements ModelContainer {
//
//    // ================== 元数据 ==================
//    private final int containerId;
//    private final String version;
//    private final String modelFilePath; // 权重文件在磁盘或对象存储上的路径
//    private final List<Parameter> parameterList;
//
//    // ================== 运行时状态 ==================
//    // 使用 AtomicReference 保证多线程下的状态切换安全
//    private final AtomicReference<ContainerStatus> status;
//    // 当前内存/显存占用 (单位: MB)，使用原子类保证并发安全
//    private final AtomicInteger runtimeMemoryUsage;
//
//    // ================== 底层引擎资源 ==================
//    // 这里的 Object 可以替换为你实际使用的引擎会话，例如：
//    // ONNX: ai.onnxruntime.OrtSession
//    // DJL: ai.djl.inference.Predictor
//    private Object modelSession;
//
//    public DynamicProcessModelContainer(int containerId, String version, String modelFilePath, List<Parameter> parameterList) {
//        this.containerId = containerId;
//        this.version = version;
//        this.modelFilePath = modelFilePath;
//        this.parameterList = parameterList;
//        this.status = new AtomicReference<>(ContainerStatus.CREATED);
//        this.runtimeMemoryUsage = new AtomicInteger(0);
//    }
//
//    // ================== 生命周期管理 ==================
//
//    @Override
//    public boolean load() {
//        // 如果已经是加载状态，直接返回成功
//        if (status.get() == ContainerStatus.LOADED) return true;
//
//        try {
//            System.out.println(">>> 容器 [" + containerId + "] 正在初始化底层 AI 引擎...");
//
//            // TODO: 1. 读取 modelFilePath 路径下的权重文件
//            // TODO: 2. 初始化底层引擎 (例如 OrtSession)
//            this.modelSession = new Object(); // 模拟引擎初始化
//
//            // 模拟模型权重加载进显存 (比如占用了 350MB)
//            this.runtimeMemoryUsage.set(350);
//
//            // 状态变更为：已就绪
//            this.status.set(ContainerStatus.LOADED);
//            return true;
//
//        } catch (Exception e) {
//            this.status.set(ContainerStatus.FAILED);
//            System.err.println("模型加载失败: " + e.getMessage());
//            return false;
//        }
//    }
//
//    @Override
//    public boolean unload() {
//        if (this.modelSession != null) {
//            // TODO: 调用 C++ 底层 API 显式释放显存 (如: ((OrtSession)this.modelSession).close();)
//            this.modelSession = null;
//        }
//
//        // 归还显存计数，状态重置
//        this.runtimeMemoryUsage.set(0);
//        this.status.set(ContainerStatus.UNLOADED);
//        System.out.println("<<< 容器 [" + containerId + "] 显存已释放。");
//        return true;
//    }
//
//    // ================== 核心推理引擎 ==================
//
//    @Override
//    public TSDataBlock run(TSDataBlock inputData) {
//        // 1. 安全检查：只有 LOADED 状态才能执行推理
//        if (!status.compareAndSet(ContainerStatus.LOADED, ContainerStatus.RUNNING)) {
//            throw new IllegalStateException("容器未处于 LOADED 状态或正在忙碌，无法接受新任务。");
//        }
//
//        try {
//            // 推理时产生的中间特征图(Feature Map)通常需要额外显存，这里模拟增加了 150MB
//            this.runtimeMemoryUsage.addAndGet(150);
//
//            System.out.println("模型 [" + containerId + "] 正在推理多要素 TSDataBlock... ");
//
//            // ======================== 核心转换流程 (伪代码) ========================
//            // 1. 数据解包 (Pre-processing)：
//            //    获取输入的动态张量维度
//            //    long[] shape = inputData.getDynamicShape();
//            //    获取多要素实体数据
//            //    Map<Integer, float[]> featureData = inputData.getFeatureDataMap();
//            //    将 Java 数据转换为 C++ 指针/张量...
//
//            // 2. 推理执行 (Inference)：
//            //    Object rawOutput = modelSession.run(转换后的张量);
//
//            // 3. 数据重装 (Post-processing)：
//            //    float[] outputFloatArray = 从 rawOutput 提取扁平数组;
//            //    创建一个新的 TSDataBlock(继承 inputData 的时空网格);
//            //    注入新的输出特征: outputBlock.addFeature(99, outputFloatArray);
//            // ======================================================================
//
//            // TODO: 返回真实转换后的 TSDataBlock
//            return null;
//
//        } catch (Exception e) {
//            this.status.set(ContainerStatus.FAILED);
//            throw new RuntimeException("推理引擎崩溃: " + e.getMessage(), e);
//        } finally {
//            // 无论成功与否，必须释放推理时的临时显存，并将状态恢复为 LOADED
//            if (status.get() != ContainerStatus.FAILED) {
//                this.runtimeMemoryUsage.addAndGet(-150);
//                this.status.set(ContainerStatus.LOADED);
//            }
//        }
//    }
//
//    // ================== Getter 方法 ==================
//    @Override public int getContainerId() { return containerId; }
//    @Override public String getVersion() { return version; }
//    @Override public List<Parameter> getParameterList() { return parameterList; }
//    @Override public ContainerStatus getStatus() { return status.get(); }
//    @Override public int getMemoryUsage() { return runtimeMemoryUsage.get(); }
//}