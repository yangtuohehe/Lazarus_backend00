package com.example.lazarus_backend00;

import com.example.lazarus_backend00.component.clock.VirtualTimeTickEvent;
import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.component.orchestration.DataStateUpdateEvent;
import com.example.lazarus_backend00.component.orchestration.ExecutableTask;
import com.example.lazarus_backend00.component.orchestration.ModelEventTrigger;
import com.example.lazarus_backend00.domain.axis.*;
import com.example.lazarus_backend00.domain.data.DataState;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.domain.data.TSState;
import com.example.lazarus_backend00.service.ModelOrchestratorService;
import com.example.lazarus_backend00.dto.TaskStatusDTO; // 🔥 引入 DTO
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 触发器核心逻辑测试：时空状态去重与任务生成编排
 */
public class TriggerOrchestrationTest {

    private static final GeometryFactory gf = new GeometryFactory();

    // =========================================================
    // 1. 制造一个“假”的编排器，专门用来拦截触发器生成的任务
    // =========================================================
    static class MockOrchestrator implements ModelOrchestratorService {
        public final List<ExecutableTask> dispatchedTasks = new ArrayList<>();

        @Override
        public void dispatchTask(ExecutableTask task) {
            dispatchedTasks.add(task);
            System.out.println("🎯 [编排器拦截] 成功捕获新任务！");
            System.out.println("   ▶️ 任务ID: " + task.getTaskId());

            if (!task.getOutputs().isEmpty() && !task.getOutputs().get(0).getTargetStates().isEmpty()) {
                Instant taskTime = task.getOutputs().get(0).getTargetStates().get(0).getTOrigin();
                System.out.println("   ▶️ 任务对应的时间步: " + taskTime);
                System.out.println("   ▶️ 包含输入端口数: " + task.getInputs().size() + " | 输出端口数: " + task.getOutputs().size());
                System.out.println("--------------------------------------------------");
            }
        }

        // 🔥 修复报错 1：实现接口中新增的 getActiveTasks 方法
        @Override
        public List<TaskStatusDTO> getActiveTasks() {
            // 在单元测试中我们不需要真实查询状态，直接返回空列表即可满足编译要求
            return new ArrayList<>();
        }
    }

    // =========================================================
    // 2. 核心测试用例
    // =========================================================
    @Test
    public void testTaskDeduplicationForExistingOutputs() {
        System.out.println("\n🚀 开始触发器编排逻辑测试 (10x10 完全重叠栅格)...\n");

        Instant startTime = Instant.parse("2026-03-05T00:00:00Z");
        MockOrchestrator mockOrchestrator = new MockOrchestrator();
        ModelEventTrigger trigger = new ModelEventTrigger(mockOrchestrator, startTime);

        List<Parameter> inputs = Arrays.asList(
                createMockParameter("INPUT", 1, 1),
                createMockParameter("INPUT", 2, 2),
                createMockParameter("INPUT", 3, 3)
        );
        List<Parameter> outputs = List.of(createMockParameter("OUTPUT", 1, 4));

// 🔥 修复点：将输入和输出合并为一个完整的模型参数列表
        List<Parameter> allParams = new ArrayList<>();
        allParams.addAll(inputs);
        allParams.addAll(outputs);

        // 🔥 将完整的 allParams 注册进触发器
        trigger.registerModel(100, allParams, Duration.ofHours(1), Duration.ofHours(24));

        Instant t3 = Instant.parse("2026-03-05T03:00:00Z");
        Instant t4 = Instant.parse("2026-03-05T04:00:00Z");
        Instant t5 = Instant.parse("2026-03-05T05:00:00Z");

        List<TSState> incomingInputs = new ArrayList<>();
        for (Instant t : Arrays.asList(t3, t4, t5)) {
            incomingInputs.add(createMockState(1, t, DataState.READY));
            incomingInputs.add(createMockState(2, t, DataState.READY));
            incomingInputs.add(createMockState(3, t, DataState.READY));
        }
        trigger.onDataStateUpdate(new DataStateUpdateEvent(this, incomingInputs, false));
        System.out.println("📥 已向总线推入 t3, t4, t5 的所有【输入数据】(Feature 1,2,3 齐备)");

        List<TSState> existingOutputs = List.of(
                createMockState(4, t5, DataState.READY)
        );
        trigger.onDataStateUpdate(new DataStateUpdateEvent(this, existingOutputs, false));
        System.out.println("📥 已向总线推入 t5 的【既有输出数据】(Feature 4 已满)");
        System.out.println("⚠️  预期行为：触发器应自动过滤掉 t5，只下发 t3 和 t4 的任务。\n");

        System.out.println("⏳ 拨动系统时钟至 t5: " + t5 + "，触发全量扫描...");
        trigger.onVirtualTimeTick(new VirtualTimeTickEvent(this, t5));

        // =========================================================
        // 3. 断言与结果验证
        // =========================================================
        System.out.println("\n📊 编排结果验证:");
        System.out.println("预期生成的任务数: 2");
        System.out.println("实际生成的任务数: " + mockOrchestrator.dispatchedTasks.size());

        assertEquals(2, mockOrchestrator.dispatchedTasks.size(), "任务数不正确，t5 没有被成功剔除！");

        boolean hasT5 = mockOrchestrator.dispatchedTasks.stream().anyMatch(task -> {
            Instant time = task.getOutputs().get(0).getTargetStates().get(0).getTOrigin();
            return time.equals(t5);
        });
        assertFalse(hasT5, "致命错误：生成了 t5 的冗余计算任务！");

        System.out.println("✅ 测试完美通过！系统成功实现了基于状态图的像素级重算规避。");
    }

    // =========================================================
    // 辅助工具：生成完美对齐的 10x10 Parameter
    // =========================================================
    private Parameter createMockParameter(String ioType, int tensorOrder, int featureId) {
        Point origin = gf.createPoint(new Coordinate(0.0, 0.0));

        SpaceAxisX xAxis = new SpaceAxisX(10.0, "Degrees", 1.0, "Degrees");
        SpaceAxisY yAxis = new SpaceAxisY(10.0, "Degrees", 1.0, "Degrees");
        TimeAxis tAxis = new TimeAxis(3600.0, "Seconds", 3600.0, "Seconds");

        List<Axis> axes = Arrays.asList(tAxis, yAxis, xAxis);

        // 🔥 修复报错 2：直接使用带参数的构造函数创建 Feature
        Feature feature = new Feature(featureId, "Feature-" + featureId);

        return new Parameter(ioType, tensorOrder, origin, axes, List.of(feature));
    }

    // =========================================================
    // 辅助工具：生成对应的满状态 TSState
    // =========================================================
    private TSState createMockState(int featureId, Instant time, DataState state) {
        TSShell shell = new TSShell.Builder(featureId)
                .time(time, new TimeAxis(3600.0, "Seconds", 3600.0, "Seconds"))
                .x(0.0, new SpaceAxisX(10.0, "Degrees", 1.0, "Degrees"))
                .y(0.0, new SpaceAxisY(10.0, "Degrees", 1.0, "Degrees"))
                .build();

        return new TSState(shell, state);
    }
}