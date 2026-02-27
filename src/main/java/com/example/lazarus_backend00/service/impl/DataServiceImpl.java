package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.component.orchestration.ExecutableTask;
import com.example.lazarus_backend00.component.orchestration.ModelEventTrigger;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.dto.subdto.DataUpdatePacket;
import com.example.lazarus_backend00.service.DataService;
import com.example.lazarus_backend00.service.ModelOrchestratorService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class DataServiceImpl implements DataService {

    private static final Logger log = LoggerFactory.getLogger(DataServiceImpl.class);

    // ================== 配置：数据子系统地址 ==================
    // 假设数据子系统和主系统在同一个 Spring Boot 应用中运行，但在逻辑上分离
    // Fetch 接口: 假设子系统有一个根据 Shell 获取 Block 的接口
    private static final String SUBSYSTEM_FETCH_URL = "http://localhost:8080/api/v1/data-subsystem/data/fetch";
    // Ingest 接口: 用于存盘仿真结果
    private static final String SUBSYSTEM_INGEST_URL = "http://localhost:8080/api/v1/data-subsystem/data/ingest-calc";

    // ================== 依赖组件 ==================
    private final ModelEventTrigger eventTrigger;
    private final ModelOrchestratorService orchestratorService;
    private final RestTemplate restTemplate;

    // ================== 本地缓存 ==================
    // 作用：暂存 TSDataBlock，供容器计算时极速读取
    private final Cache<String, TSDataBlock> dataCache = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .recordStats()
            .build();

    // 构造器注入 (@Lazy 解决潜在的循环依赖)
    public DataServiceImpl(ModelEventTrigger eventTrigger,
                           @Lazy ModelOrchestratorService orchestratorService) {
        this.eventTrigger = eventTrigger;
        this.orchestratorService = orchestratorService;
        this.restTemplate = new RestTemplate();
    }

    // =================================================================
    // 1. 【通知处理】 来自数据子系统的实测数据通知
    // =================================================================
    @Override
    public void notifyDataArrivals(List<DataUpdatePacket> packets) {
        if (packets == null || packets.isEmpty()) return;

        log.info("🔔 [DataService] 收到 {} 个数据更新通知", packets.size());

        for (DataUpdatePacket packet : packets) {
            // 1. 清除旧缓存 (因为实测数据更新了，旧缓存可能过期)
            // String key = generateKey(packet.getShell().getFeatureId(), ...);
            // dataCache.invalidate(key); // 简单起见，这里暂不精细化清除，依赖 TTL

            // 2. 触发触发器 (UDCR)
            // status: 1=New, 2=Replace
            List<ExecutableTask> tasks = eventTrigger.onDataUpdate(packet.getShell(), packet.getStatus());

            // 3. 驱动编排器执行任务
            if (!tasks.isEmpty()) {
                log.info("    ⚡ 触发 {} 个任务 (Feature: {})", tasks.size(), packet.getShell().getFeatureId());
                for (ExecutableTask task : tasks) {
                    orchestratorService.dispatchTask(task);
                }
            }
        }
    }

    // =================================================================
    // 2. 【写逻辑】 仿真结果回写 & 级联触发
    // =================================================================
    @Override
    public void pushData(int featureId, TSDataBlock dataBlock) {
        // 1. 远程归档：直接发送给数据子系统存盘
        try {
            restTemplate.postForObject(SUBSYSTEM_INGEST_URL, dataBlock, String.class);
        } catch (Exception e) {
            log.error("❌ [Archive Failed] 归档失败: {}", e.getMessage());
        }

        // 2. 级联触发：从 Block 提取 Shell
        TSShell shell = extractShellFromBlock(featureId, dataBlock);

        // 3. 触发下游模型 (状态固定为 2：模拟数据)
        List<ExecutableTask> tasks = eventTrigger.onDataUpdate(shell, 2);

        if (!tasks.isEmpty()) {
            log.info("    🌊 [Cascade] 仿真结果 F{} 级联触发 {} 个新任务", featureId, tasks.size());
            for (ExecutableTask task : tasks) {
                orchestratorService.dispatchTask(task);
            }
        }

        // 方法结束，dataBlock 将在调用链结束后被 JVM 自动回收
    }

    // =================================================================
    // 3. 【读逻辑】 模型获取输入数据
    // =================================================================
    @Override
    public TSDataBlock fetchData(TSShell requirementShell) {
        try {
            // 取消了一级缓存，所有计算所需数据直接向数据系统现用现取
            return restTemplate.postForObject(
                    SUBSYSTEM_FETCH_URL,
                    requirementShell,
                    TSDataBlock.class
            );
        } catch (Exception e) {
            log.error("❌ [Fetch Failed] 远程取数失败: {}", e.getMessage());
        }
        return null;
    }

    // ================== 辅助方法 ==================
    private String generateKey(int featureId, long timestamp) {
        return featureId + "_" + timestamp;
    }

    private TSShell extractShellFromBlock(int featureId, TSDataBlock block) {
        return new TSShell.Builder(featureId)
                .time(block.getTOrigin(), block.getTAxis())
                .z(block.getZOrigin(), block.getZAxis())
                .y(block.getYOrigin(), block.getYAxis())
                .x(block.getXOrigin(), block.getXAxis())
                .build();
    }
}