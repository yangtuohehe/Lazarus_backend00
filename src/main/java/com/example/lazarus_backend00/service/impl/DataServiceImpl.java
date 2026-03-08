package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.component.orchestration.DataStateUpdateEvent;
import com.example.lazarus_backend00.domain.axis.TimeAxis;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSState;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.service.DataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class DataServiceImpl implements DataService {

    private static final Logger log = LoggerFactory.getLogger(DataServiceImpl.class);

    private static final String SUBSYSTEM_FETCH_URL = "http://localhost:8080/api/v1/data-subsystem/data/fetch";
    private static final String SUBSYSTEM_INGEST_URL = "http://localhost:8080/api/v1/data-subsystem/data/ingest-calc";

    private final ApplicationEventPublisher eventPublisher;
    private final RestTemplate restTemplate;

    public DataServiceImpl(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public void notifyDataArrivals(List<TSState> incomingStates) {
        if (incomingStates == null || incomingStates.isEmpty()) return;
        // 数据端经过筛查后发来的状态，直接送入事件总线交由触发器判断
        boolean hasReplaced = incomingStates.stream().anyMatch(TSState::hasReplacedData);
        eventPublisher.publishEvent(new DataStateUpdateEvent(this, incomingStates, hasReplaced));
    }

    @Override
    public void pushData(int featureId, TSDataBlock dataBlock) {
        // 🎯 严格遵循算法：模型输出只向数据端发送，绝对不在此处向事件总线发消息 (禁止自循环)
        try {
            restTemplate.postForObject(SUBSYSTEM_INGEST_URL, dataBlock, String.class);
            log.info("💾 [DataService] Data block for FeatureID={} successfully dispatched to storage subsystem. Awaiting Subsystem Pruning...", featureId);
        } catch (Exception e) {
            log.error("❌ [DataService] Data ingest failed: {}", e.getMessage());
        }
        // ⚠️ 删除了之前那一整段从 rawData 反向解析 TSState 并 publishEvent 的逻辑
        // 真正的判定权交还给了数据子系统的“筛查”机制
    }

    @Override
    public TSDataBlock fetchData(TSShell requirementShell) {
        try {
            return restTemplate.postForObject(SUBSYSTEM_FETCH_URL, requirementShell, TSDataBlock.class);
        } catch (Exception e) {
            log.error("❌ [DataService] Fetch data failed for FeatureID={}, Time={}", requirementShell.getFeatureId(), requirementShell.getTOrigin());
            return null;
        }
    }
}