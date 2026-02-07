package com.example.lazarus_backend00.service;

import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;

import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * 数据预取服务接口
 * 职责：提供基于 TaskID 的异步数据加载和同步获取功能。
 */
public interface DataPreloadService {

    /**
     * [生产者] 启动异步取数
     * 立即返回，后台执行 IO 操作。
     *
     * @param taskId 任务唯一凭证
     * @param inputShells 数据需求清单
     */
    void startFetching(long taskId, List<TSShell> inputShells);

    /**
     * [消费者] 凭票取数
     * 阻塞等待直到数据就绪或超时。
     *
     * @param taskId 任务唯一凭证
     * @param timeoutSeconds 最大等待时间
     * @return 真实的数据列表
     * @throws TimeoutException 等待超时
     * @throws Exception 其他数据获取错误
     */
    List<TSDataBlock> getData(long taskId, long timeoutSeconds) throws Exception;
}