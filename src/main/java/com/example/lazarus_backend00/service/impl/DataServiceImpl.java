package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.component.orchestration.DataStateUpdateEvent;
import com.example.lazarus_backend00.domain.data.DataState;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSState;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.domain.data.TSShellFactory;
import com.example.lazarus_backend00.dto.subdto.DataUpdatePacket;
import com.example.lazarus_backend00.service.DataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
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

    // =================================================================
    // 1. 【通知处理】 接收来自 Controller 转发的数据端 TSState 广播
    // =================================================================
    @Override
    public void notifyDataArrivals(List<TSState> incomingStates) {
        if (incomingStates == null || incomingStates.isEmpty()) return;

        boolean hasReplaced = false;

        // 遍历检查这批状态里是否包含“脏数据替换” (DataState.REPLACED)
        // 注意：这里的 DataState 枚举是在 TSState 中的 dataState 属性
        for (TSState state : incomingStates) {
            if (state.hasReplacedData()) {
                hasReplaced = true;
                break;
            }
        }

        // 🔥 直接把这批 TSState 封装成事件，扔进 Spring 总线
        // 触发器 (ModelEventTrigger) 会在暗中接管一切！
        eventPublisher.publishEvent(new DataStateUpdateEvent(this, incomingStates, hasReplaced));
    }

    @Override
    public void pushData(int featureId, TSDataBlock dataBlock) {
        try {
            restTemplate.postForObject(SUBSYSTEM_INGEST_URL, dataBlock, String.class);
        } catch (Exception e) {
            log.error("❌ 归档失败: {}", e.getMessage());
        }

        // TSDataBlock 直接多态转换为 TSShell，生成全屏置 1 (READY) 的位图状态包
        TSState tsState = TSShellFactory.createTSStateFromShell(dataBlock, DataState.READY);
        List<TSState> stateUpdates = new ArrayList<>();
        stateUpdates.add(tsState);

        eventPublisher.publishEvent(new DataStateUpdateEvent(this, stateUpdates, false));
    }

    @Override
    public TSDataBlock fetchData(TSShell requirementShell) {
        try {
            return restTemplate.postForObject(SUBSYSTEM_FETCH_URL, requirementShell, TSDataBlock.class);
        } catch (Exception e) {
            log.error("❌ 取数失败: {}", e.getMessage());
        }
        return null;
    }
}