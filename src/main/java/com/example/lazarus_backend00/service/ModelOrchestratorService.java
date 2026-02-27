package com.example.lazarus_backend00.service;
import com.example.lazarus_backend00.component.orchestration.ExecutableTask;
import com.example.lazarus_backend00.dto.TaskStatusDTO;

import java.time.Instant;
import java.util.List;

/**
 * 模型编排服务接口
 * 职责：接收信号，分析任务，调度执行。
 */
public interface ModelOrchestratorService {


    /**
     * [执行入口] 分发单个计算任务
     * 通常由 onDataChanged 内部调用，但也暴露给外部用于手动重试或调度。
     *
     * @param task 可执行任务单 (包含 TaskID, ContainerID, I/O定义)
     */
    void dispatchTask(ExecutableTask task);
    // 🔥 新增：获取当前正在系统中的活跃任务列表
    List<TaskStatusDTO> getActiveTasks();
}