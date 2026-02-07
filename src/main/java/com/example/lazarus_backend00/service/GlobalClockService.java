package com.example.lazarus_backend00.service;
import java.time.Duration;
import java.time.Instant;
/**
 * 全局仿真时钟服务接口
 * 定义了岛礁数字孪生系统的时间控制标准
 */
public interface GlobalClockService {

    // ===========================
    // 🎮 核心控制 (Controls)
    // ===========================

    /**
     * 重置时钟状态
     * @param startTime 虚拟时间的起始点（例如：2020-01-01 00:00:00）
     * @param stepSize  步长（每次跳动前进多久，例如：1小时）
     */
    void reset(Instant startTime, Duration stepSize);

    /**
     * 启动自动演练
     * 按照设定的播放速度自动推进时间
     */
    void play();

    /**
     * 暂停演练
     * 停止自动时间推进，系统停留在当前虚拟时刻
     */
    void pause();

    /**
     * 手动单步执行
     * 无论当前是否暂停，强制推进一个步长（用于 Debug 或 逐帧分析）
     */
    void stepOnce();

    /**
     * 设置播放速度（仅影响自动播放模式）
     * @param speedMs 现实世界中每隔多少毫秒执行一次 Tick
     */
    void setPlaybackSpeed(long speedMs);

    // ===========================
    // 🔍 状态查询 (Queries)
    // ===========================

    /**
     * 获取当前虚拟时间
     * @return 当前仿真时刻
     */
    Instant getCurrentVirtualTime();

    /**
     * 查询是否正在自动运行
     * @return true=正在自动播放, false=暂停状态
     */
    boolean isRunning();

    /**
     * 获取当前的步长设置
     * @return 时间步长
     */
    Duration getStepSize();
}
