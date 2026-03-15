package com.example.lazarus_backend00;

import com.example.lazarus_backend00.component.orchestration.ModelEventTrigger;
import com.example.lazarus_backend00.component.pool.ModelContainerPool;
import com.example.lazarus_backend00.dto.ModelContainerDTO;
import com.example.lazarus_backend00.service.ContainerPoolService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Field;
import java.util.Map;

@SpringBootTest
public class ModelRegistrationTest {

    @Autowired(required = false)
    private ModelEventTrigger modelEventTrigger;

    @Autowired(required = false)
    private ModelContainerPool modelContainerPool;

    // ==========================================================
    // 🎯 注入你的最高层大管家服务！
    // ==========================================================
    @Autowired
    private ContainerPoolService containerPoolService;

    @Test
    public void testFullRegistrationFlow() throws Exception {
        Integer targetModelId = 2;
        // 🔥 新增：定义接口ID。传入 null 表示在测试中直接使用该模型的默认参数接口
        Integer targetInterfaceId = null;

        System.out.println("\n🚀 第一步：调用 ContainerPoolService 进行全流程实例化与注册...");

        try {
            // ==========================================================
            // 🔥 核心点火：直接调你的真实业务接口！
            // ==========================================================
            ModelContainerDTO dto = containerPoolService.registerContainer(targetModelId, targetInterfaceId);

            System.out.println("   ✅ 成功调用 registerContainer！");
            System.out.println("   ↳ 分配到的 Container ID: " + dto.getId());
            System.out.println("   ↳ 模型名称: " + dto.getModelName());
            System.out.println("   ↳ 当前状态: " + dto.getStatus());

        } catch (Exception e) {
            System.err.println("   ❌ 注册失败！报错信息：");
            e.printStackTrace();
            return;
        }


        System.out.println("\n================ 🕵️ 运行时态全盘扫描 ================\n");

        // ------------------------------------------------------------------
        // 1. 扫描 ModelContainerPool 容器池 (既然是 PoolService，池子里必须有！)
        // ------------------------------------------------------------------
        System.out.println("🔍 [1] 扫描 ModelContainerPool 容器池...");
        if (modelContainerPool != null) {
            Field[] fields = ModelContainerPool.class.getDeclaredFields();
            boolean poolFoundAndNotEmpty = false;

            for (Field field : fields) {
                if (Map.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Map<?, ?> map = (Map<?, ?>) field.get(modelContainerPool);

                    if (!map.isEmpty()) {
                        System.out.println("   ✅ 完美！在容器池的 [" + field.getName() + "] 集合中，发现了活跃的 Container ID: " + map.keySet());
                        poolFoundAndNotEmpty = true;
                    }
                }
            }
            if (!poolFoundAndNotEmpty) {
                System.err.println("   ❌ 失败：你的 registerContainer 跑完了，但池子里居然是空的！请检查 ContainerPoolService 内部实现！");
            }
        }

        System.out.println("\n------------------------------------------------------------\n");

        // ------------------------------------------------------------------
        // 2. 扫描 ModelEventTrigger 触发器注册表
        // ------------------------------------------------------------------
        System.out.println("🔍 [2] 扫描 ModelEventTrigger 触发器...");
        if (modelEventTrigger != null) {
            Field registryField = ModelEventTrigger.class.getDeclaredField("registry");
            registryField.setAccessible(true);
            Map<Integer, Object> registry = (Map<Integer, Object>) registryField.get(modelEventTrigger);

            if (registry.isEmpty()) {
                System.err.println("   ⚠️ 警告：触发器是空的！这说明你的 ContainerPoolService 只把模型放进了池子，但【没有】调用 modelEventTrigger.registerModel(...) 把它交给触发器去监听时间轴！");
            } else {
                System.out.println("   ✅ 完美！触发器中也成功注册了！活跃的 Container ID 是: " + registry.keySet());
            }
        }

        System.out.println("\n=========================================================================\n");
    }
}