package com.example.lazarus_backend00.service;

import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;

/**
 * 数据服务接口 (Service Layer Interface)
 * 职责：作为系统的核心数据总线，负责数据的 注入(Push) 和 查询(Fetch)。
 */
public interface DataService {

    /**
     * 【写入口】外部数据注入
     * 将标准化后的数据块推入系统。
     * 行为：
     * 1. 将数据存入高速缓存 (Pre-loading)。
     * 2. (可选) 异步持久化到数据库。
     * 3. 通知模型触发器 (EventTrigger) 有新数据到达。
     *
     * @param featureId 特征 ID (如 101)
     * @param dataBlock 标准数据块 (包含真实数据)
     */
    void pushData(int featureId, TSDataBlock dataBlock);

    /**
     * 【读入口】容器数据查询
     * 模型容器在计算前调用此接口获取输入数据。
     * 行为：
     * 1. 优先查找高速缓存 (Cache Hit)。
     * 2. (可选) 缓存未命中则查询数据库 (Cache Miss)。
     *
     * @param requirementShell 包含所需的时间、空间范围的元数据外壳
     * @return 完整的数据块，如果未找到则返回 null
     */
    TSDataBlock fetchData(TSShell requirementShell);
}