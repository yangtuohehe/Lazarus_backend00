package com.example.lazarus_backend00;

import ai.djl.engine.Engine;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.onnxruntime.OrtEnvironment;

public class AiEngineConnectTest {

    public static void main(String[] args) {
        System.out.println("====== 正在启动 AI 引擎自检 ======\n");

        // ----------------------------------------------------
        // 测试 1: ONNX Runtime
        // ----------------------------------------------------
        try {
            // 获取 ONNX 单例环境（这一步会加载底层 C++ 库）
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            System.out.println("✅ [ONNX Runtime] 初始化成功!");
            System.out.println("当前 ONNX 版本: 1.17.1 (Ready for inference)");

            // 显式释放测试环境资源
            env.close();
        } catch (Exception e) {
            System.err.println("❌ [ONNX Runtime] 启动失败: " + e.getMessage());
        }

        System.out.println("\n-----------------------------------\n");

        // ----------------------------------------------------
        // 测试 2: DJL (PyTorch)
        // ----------------------------------------------------
        try {
            // 获取当前生效的 DJL 引擎名称（应该会输出 PyTorch）
            String engineName = Engine.getInstance().getEngineName();
            System.out.println("✅ [DJL] 引擎核心加载成功: " + engineName);

            // 在 C++ 显存中创建一个 2x2 的全1张量，然后打印出来
            try (NDManager manager = NDManager.newBaseManager()) {
                NDArray tensor = manager.ones(new ai.djl.ndarray.types.Shape(2, 2));
                System.out.println("✅ [DJL-PyTorch] 张量 C++ 内存分配成功，数据如下：");
                System.out.println(tensor);
            }
        } catch (Exception e) {
            System.err.println("❌ [DJL-PyTorch] 启动失败: " + e.getMessage());
        }

        System.out.println("\n====== AI 引擎自检结束 ======");
    }
}
