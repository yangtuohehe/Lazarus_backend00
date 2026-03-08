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
import com.example.lazarus_backend00.dto.TaskStatusDTO;
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

            // 🎯 修复报错点 1：适配新的二维状态列表结构
            if (!task.getOutputs().isEmpty() && !task.getOutputs().get(0).getTargetStatesPerFeature().isEmpty()) {
                // 获取第一个输出端口 -> 第一个特征 -> 它的时间序列帧列表 -> 提取第一帧的时间点
                List<TSState> featureStates = task.getOutputs().get(0).getTargetStatesPerFeature().get(0);
                if (!featureStates.isEmpty()) {
                    Instant taskTime = featureStates.get(0).getTOrigin();
                    System.out.println("   ▶️ 任务对应的时间步: " + taskTime);
                    System.out.println("   ▶️ 包含输入端口数: " + task.getInputs().size() + " | 输出端口数: " + task.getOutputs().size());
                    System.out.println("--------------------------------------------------");
                }
            }
        }

        @Override
        public List<TaskStatusDTO> getActiveTasks() {
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

        List<Parameter> allParams = new ArrayList<>();
        allParams.addAll(inputs);
        allParams.addAll(outputs);

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
            // 🎯 修复报错点 2：在断言中适配新的二维状态列表结构
            List<TSState> featureStates = task.getOutputs().get(0).getTargetStatesPerFeature().get(0);
            if (featureStates.isEmpty()) return false;

            Instant time = featureStates.get(0).getTOrigin();
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

        // 模拟一个单步长的时间轴 (count = 1)
        TimeAxis tAxis = new TimeAxis(3600.0, "Seconds", 3600.0, "Seconds");
        tAxis.setCount(1);

        List<Axis> axes = Arrays.asList(tAxis, yAxis, xAxis);

        Feature feature = new Feature(featureId, "Feature-" + featureId);

        return new Parameter(ioType, tensorOrder, 0, origin, axes, List.of(feature));
    }

    // =========================================================
    // 辅助工具：生成对应的满状态 TSState
    // =========================================================
    private TSState createMockState(int featureId, Instant time, DataState state) {
        TimeAxis tAxis = new TimeAxis(3600.0, "Seconds", 3600.0, "Seconds");
        tAxis.setCount(1);

        TSShell shell = new TSShell.Builder(featureId)
                .time(time, tAxis)
                .x(0.0, new SpaceAxisX(10.0, "Degrees", 1.0, "Degrees"))
                .y(0.0, new SpaceAxisY(10.0, "Degrees", 1.0, "Degrees"))
                .build();

        return new TSState(shell, state);
    }
}