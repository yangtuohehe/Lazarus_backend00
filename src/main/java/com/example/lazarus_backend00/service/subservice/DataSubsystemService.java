package com.example.lazarus_backend00.service.subservice;

import java.time.Instant;

import com.example.lazarus_backend00.component.clock.VirtualTimeTickEvent;
import java.time.Instant;

/**
 * 数据子系统仿真服务接口
 * 职责：
 * 1. 作为【生产者】，负责驱动实测数据（ERA5, Meiji等）的生成。
 * 2. 监听虚拟时钟事件，自动触发数据生成逻辑。
 * 3. 负责向主系统发送数据到达通知 (HTTP)。
 */
public interface DataSubsystemService {

    /**
     * [事件驱动入口] 响应虚拟时钟跳动
     * 当 GlobalClock 发布 Tick 事件时，自动调用此方法。
     *
     * @param event 虚拟时钟事件
     */
    void onVirtualTimeTick(VirtualTimeTickEvent event);

    /**
     * [手动触发入口] 执行数据摄入仿真
     * 保留此接口用于 Controller 手动触发 (/sync 接口) 或测试用途。
     *
     * @param currentSystemTime 当前虚拟系统时间
     */
    void executeIngestion(Instant currentSystemTime);
}