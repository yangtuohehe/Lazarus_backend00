//package com.example.lazarus_backend00.service.impl;
//
//import com.example.lazarus_backend00.component.orchestration.ModelEventTrigger;
//import com.example.lazarus_backend00.domain.data.*;
//import com.example.lazarus_backend00.service.DataService;
//import com.github.benmanes.caffeine.cache.Cache;
//import com.github.benmanes.caffeine.cache.Caffeine;
//import org.springframework.stereotype.Service;
//
//import java.util.concurrent.TimeUnit;
//
//@Service
//public class DataServiceImpl implements DataService {
//
//    // ================== 依赖组件 ==================
//    private final ModelEventTrigger eventTrigger;
//
//    // ================== 本地缓存配置 ==================
//    // 作用：暂存 TSDataBlock，供容器计算时极速读取
//    private final Cache<String, TSDataBlock> dataCache = Caffeine.newBuilder()
//            .maximumSize(2000)                   // 最大缓存数量
//            .expireAfterWrite(1, TimeUnit.HOURS) // 写入后1小时过期
//            .recordStats()                       // 开启监控
//            .build();
//
//    // 构造器注入
//    public DataServiceImpl(ModelEventTrigger eventTrigger) {
//        this.eventTrigger = eventTrigger;
//    }
//
//    // ================== 接口实现：写逻辑 ==================
//    @Override
//    public void pushData(int featureId, TSDataBlock dataBlock) {
//        // 1. 生成缓存 Key
//        String key = generateKey(featureId, dataBlock.getTOrigin().getEpochSecond());
//
//        // 2. 存入缓存 (Pre-load)
//        dataCache.put(key, dataBlock);
//        System.out.println(">>> [DataService] 数据已缓存: " + key );
//
//        // 3. 构建元数据 Shell 用于通知
//        // (这里需要从 Block 反向提取 Shell，或者由上层传入，此处演示反向提取)
//        TSShell shell = extractShellFromBlock(featureId, dataBlock);
//
//        // 4. 触发下游模型
//        eventTrigger.onDataUpdate(shell);
//    }
//
//    // ================== 接口实现：读逻辑 ==================
//    @Override
//    public TSDataBlock fetchData(TSShell requirementShell) {
//        // 1. 生成查询 Key
//        String key = generateKey(
//                requirementShell.getFeatureId(),
//                requirementShell.getTOrigin().getEpochSecond()
//        );
//
//        // 2. 查询缓存
//        TSDataBlock block = dataCache.getIfPresent(key);
//
//        if (block != null) {
//            return block;
//        }
//
//        // 3. 缓存未命中处理
//        // 根据你的纯净服务理念，这里可以直接报错，或者记录日志
//        System.err.println("⚠️ [DataService] 缓存未命中 (Cache Miss): " + key + "。请确保数据已提前 Push。");
//        return null;
//    }
//
//    // ================== 私有辅助方法 ==================
//
//    private String generateKey(int featureId, long timestamp) {
//        return featureId + "_" + timestamp;
//    }
//
//    /**
//     * 辅助工具：从重型 DataBlock 提取轻量级 Shell
//     */
//    private TSShell extractShellFromBlock(int featureId, TSDataBlock block) {
//        return new TSShell.Builder(featureId)
//                .time(block.getTOrigin(), block.getTAxis())
//                .z(block.getZOrigin(), block.getZAxis())
//                .y(block.getYOrigin(), block.getYAxis())
//                .x(block.getXOrigin(), block.getXAxis())
//                .build();
//    }
//}