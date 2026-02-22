package com.example.lazarus_backend00.service;

import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.dto.subdto.DataUpdatePacket; // 确保引用了正确的 DTO

import java.util.List;

/**
 * 数据服务接口 (Core Data Bus)
 */
public interface DataService {

    /**
     * 【写入口】外部数据注入 / 结果回写
     * 1. 存入本地缓存
     * 2. (如果是结果) 发送给数据子系统存盘
     * 3. 触发内部级联计算 (Status=2)
     */
    void pushData(int featureId, TSDataBlock dataBlock);

    /**
     * 【读入口】获取数据
     * 1. 查本地缓存
     * 2. 缓存未命中 -> HTTP 请求数据子系统
     */
    TSDataBlock fetchData(TSShell requirementShell);

    /**
     * 【通知入口】处理来自数据子系统的批量通知
     * 1. 解析数据包
     * 2. 触发 EventTrigger
     * 3. 驱动 Orchestrator 执行任务
     */
    void notifyDataArrivals(List<DataUpdatePacket> packets);
}