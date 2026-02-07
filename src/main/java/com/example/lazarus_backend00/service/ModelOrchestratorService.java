package com.example.lazarus_backend00.service;
import com.example.lazarus_backend00.component.orchestration.ExecutableTask;

import java.time.Instant;

/**
 * 模型编排服务接口
 * 职责：接收信号，分析任务，调度执行。
 */
public interface ModelOrchestratorService {

    /**
     * [信号入口] 数据变更通知
     * 当外部数据注入或内部模型计算完成时调用。
     *
     * @param featureId 变更的特征ID
     * @param start 数据起始时间
     * @param end 数据结束时间
     */
    void onDataChanged(int featureId, Instant start, Instant end);

    /**
     * [执行入口] 分发单个计算任务
     * 通常由 onDataChanged 内部调用，但也暴露给外部用于手动重试或调度。
     *
     * @param task 可执行任务单 (包含 TaskID, ContainerID, I/O定义)
     */
    void dispatchTask(ExecutableTask task);
}