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

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 触发器高阶编排逻辑测试：包含模型级联 (DAG)、替换态链式重算与防重入校验
 */
public class TriggerOrchestrationTest {

    private static final GeometryFactory gf = new GeometryFactory();

    // =========================================================
    // 1. 制造一个“假”的编排器，捕获任务并利用反射提取私有掩膜标志
    // =========================================================
    static class MockOrchestrator implements ModelOrchestratorService {
        public final List<ExecutableTask> dispatchedTasks = new ArrayList<>();

        @Override
        public void dispatchTask(ExecutableTask task) {
            dispatchedTasks.add(task);

            // 提取私有的 applyMask 字段，用于验证第二路无掩膜全覆盖逻辑
            boolean mask = true;
            try {
                Field field = ExecutableTask.class.getDeclaredField("applyMask");
                field.setAccessible(true);
                mask = (boolean) field.get(task);
            } catch (Exception ignored) {}

            System.out.printf("🎯 [编排器拦截] 捕获任务! 任务ID: %d | 模型容器ID: %d | 启用掩膜 (applyMask): %b\n",
                    task.getTaskId(), task.getContainerId(), mask);
        }

        @Override
        public List<TaskStatusDTO> getActiveTasks() { return new ArrayList<>(); }
    }

    // =========================================================
    // 2. 核心高阶测试用例
    // =========================================================
    @Test
    public void testComplexCascadeAndDeduplication() throws Exception {
        System.out.println("\n🚀 ====== 开始高级触发器编排测试 (DAG级联与替换态重算) ======\n");

        Instant startTime = Instant.parse("2026-03-05T00:00:00Z");
        MockOrchestrator mockOrchestrator = new MockOrchestrator();
        ModelEventTrigger trigger = new ModelEventTrigger(mockOrchestrator, startTime);

        // ---------------------------------------------------------
        // 构建模型依赖链 (DAG): 模型 A(101) 的输出，是模型 B(102) 的输入
        // ---------------------------------------------------------

        // 模型 A (ID:101): F1, F2 -> F3
        List<Parameter> paramsA = Arrays.asList(
                createMockParameter("INPUT", 1, 1),
                createMockParameter("INPUT", 2, 2),
                createMockParameter("OUTPUT", 1, 3)
        );
        trigger.registerModel(101, paramsA);

        // 模型 B (ID:102): F3 -> F4
        List<Parameter> paramsB = Arrays.asList(
                createMockParameter("INPUT", 1, 3),
                createMockParameter("OUTPUT", 1, 4)
        );
        trigger.registerModel(102, paramsB);

        System.out.println("✅ 模型A (101) 与 模型B (102) 注册成功，形成级联依赖：F1,F2 -> F3 -> F4");

        Instant t1 = Instant.parse("2026-03-05T01:00:00Z");
        Instant t2 = Instant.parse("2026-03-05T02:00:00Z");
        Instant t3 = Instant.parse("2026-03-05T03:00:00Z");
        Instant t4 = Instant.parse("2026-03-05T04:00:00Z");
        Instant t5 = Instant.parse("2026-03-05T05:00:00Z");

        // ---------------------------------------------------------
        // 第一阶段测试：正常补漏事件 (DataState.READY)
        // ---------------------------------------------------------
        System.out.println("\n--- 阶段1：常规数据到达与空洞补漏扫描 ---");
        List<TSState> incomingF1F2 = new ArrayList<>();
        for (Instant t : Arrays.asList(t1, t2, t3, t4)) {
            incomingF1F2.add(createMockState(1, t, DataState.READY));
            incomingF1F2.add(createMockState(2, t, DataState.READY));
        }
        // 发布到事件总线，触发器路由给关心的模型
        trigger.onDataStateUpdate(new DataStateUpdateEvent(this, incomingF1F2, false));
        System.out.println("📥 已推入 t1~t4 的 F1, F2 传感器数据 (状态1:READY)。");

        // 模拟时钟推演到 t5
        System.out.println("⏳ 拨动系统时钟至 t5，触发全量空洞扫描...");
        trigger.onVirtualTimeTick(new VirtualTimeTickEvent(this, t5));

        // 断言验证阶段 1
        // 预期：模型A在 t1~t4 共发现4个空洞，生成4次任务 (带掩膜保护)
        // 模型B由于输入F3还未计算完成(依然是0)，不满足条件，跳过不生成。
        assertEquals(4, mockOrchestrator.dispatchedTasks.size(), "阶段1：应该生成4个任务");
        long modelATasks = mockOrchestrator.dispatchedTasks.stream()
                .filter(t -> t.getContainerId() == 101 && isMaskEnabled(t))
                .count();
        assertEquals(4, modelATasks, "阶段1：模型A必须生成4个带掩膜(applyMask=true)的补漏任务！");


        // ---------------------------------------------------------
        // 第二阶段测试：替换态纠偏事件 (DataState.REPLACED)
        // ---------------------------------------------------------
        System.out.println("\n--- 阶段2：实测数据同化，触发纠偏替换(REPLACED) ---");
        System.out.println("📥 模拟底层数据管家筛查后，向总线通报：t2 和 t3 的 F3 数据被实测纠偏！(状态2:REPLACED)");

        List<TSState> replacedF3 = Arrays.asList(
                createMockState(3, t2, DataState.REPLACED),
                createMockState(3, t3, DataState.REPLACED)
        );
        // 推送状态为 2 的事件！
        trigger.onDataStateUpdate(new DataStateUpdateEvent(this, replacedF3, true));

        // 断言验证阶段 2
        // 预期：模型B作为下游，发现输入含有状态 2，立刻生成 2 个全量洗盘任务 (无掩膜)！
        assertEquals(6, mockOrchestrator.dispatchedTasks.size(), "阶段2：累计任务应该达到6个");
        long modelBTasks = mockOrchestrator.dispatchedTasks.stream()
                .filter(t -> t.getContainerId() == 102 && !isMaskEnabled(t)) // 验证 applyMask == false
                .count();
        assertEquals(2, modelBTasks, "阶段2：模型B必须生成2个无掩膜(applyMask=false)的洗盘重算任务！");


        // ---------------------------------------------------------
        // 第三阶段测试：数据状态原位更新 (防止死循环校验)
        // ---------------------------------------------------------
        System.out.println("\n--- 阶段3：防重入与沙盘状态更新校验 ---");
        System.out.println("⏳ 再次拨动系统时钟触发全面扫描 (检验沙盘内的2是否已自动降级为1，防止重复生成任务)...");

        int taskCountBeforeTick = mockOrchestrator.dispatchedTasks.size(); // 当前为 6
        trigger.onVirtualTimeTick(new VirtualTimeTickEvent(this, t5));

        // 断言验证阶段 3
        assertEquals(taskCountBeforeTick, mockOrchestrator.dispatchedTasks.size(),
                "阶段3失败！状态没有降级，导致再次扫描时生成了冗余的死循环任务！");

        System.out.println("✅ 测试完美通过！模型级联触发正常，2转1状态机自动降级成功阻断了死循环。");
    }

    // =========================================================
    // 反射辅助方法
    // =========================================================
    private boolean isMaskEnabled(ExecutableTask task) {
        try {
            Field field = ExecutableTask.class.getDeclaredField("applyMask");
            field.setAccessible(true);
            return (boolean) field.get(task);
        } catch (Exception e) { return false; }
    }

    // =========================================================
    // 辅助工具：生成完美对齐的 10x10 Parameter
    // =========================================================
    private Parameter createMockParameter(String ioType, int tensorOrder, int featureId) {
        Point origin = gf.createPoint(new Coordinate(0.0, 0.0));

        SpaceAxisX xAxis = new SpaceAxisX(10.0, "Degrees", 1.0, "Degrees");
        xAxis.setType("SPACE_X");
        xAxis.setCount(10);

        SpaceAxisY yAxis = new SpaceAxisY(10.0, "Degrees", 1.0, "Degrees");
        yAxis.setType("SPACE_Y");
        yAxis.setCount(10);

        TimeAxis tAxis = new TimeAxis(3600.0, "Seconds", 3600.0, "Seconds");
        tAxis.setType("TIME");    // 必须显式声明
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
        tAxis.setType("TIME");
        tAxis.setCount(1);

        SpaceAxisX xAxis = new SpaceAxisX(10.0, "Degrees", 1.0, "Degrees");
        xAxis.setType("SPACE_X"); xAxis.setCount(10);

        SpaceAxisY yAxis = new SpaceAxisY(10.0, "Degrees", 1.0, "Degrees");
        yAxis.setType("SPACE_Y"); yAxis.setCount(10);

        TSShell shell = new TSShell.Builder(featureId)
                .time(time, tAxis)
                .x(0.0, xAxis)
                .y(0.0, yAxis)
                .build();

        return new TSState(shell, state);
    }
}