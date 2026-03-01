package com.example.lazarus_backend00.dto;

import java.time.Instant;

/**
 * 任务状态传输对象 (用于前端展示)
 */
public class TaskStatusDTO {
    private long taskId;
    private int containerId;
    private String status;      // 状态: PENDING(排队中), PREPARING_DATA(拉取数据), COMPUTING(计算中), ERROR(异常)
    private Instant startTime;  // 任务触发时间
    private String message;     // 附加信息

    public TaskStatusDTO() {}

    public TaskStatusDTO(long taskId, int containerId, String status, Instant startTime, String message) {
        this.taskId = taskId;
        this.containerId = containerId;
        this.status = status;
        this.startTime = startTime;
        this.message = message;
    }

    public long getTaskId() { return taskId; }
    public void setTaskId(long taskId) { this.taskId = taskId; }

    public int getContainerId() { return containerId; }
    public void setContainerId(int containerId) { this.containerId = containerId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}