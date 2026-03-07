package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.component.orchestration.DataStateUpdateEvent;
import com.example.lazarus_backend00.domain.axis.TimeAxis;
import com.example.lazarus_backend00.domain.data.DataState;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSState;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.domain.data.TSShellFactory;
import com.example.lazarus_backend00.service.DataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
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
        boolean hasReplaced = incomingStates.stream().anyMatch(TSState::hasReplacedData);
        eventPublisher.publishEvent(new DataStateUpdateEvent(this, incomingStates, hasReplaced));
    }

    @Override
    public void pushData(int featureId, TSDataBlock dataBlock) {
        // 1. 发送给子系统持久化 (落地为 TIF 文件)
        try {
            restTemplate.postForObject(SUBSYSTEM_INGEST_URL, dataBlock, String.class);
            log.info("💾 [DataService] Data block for FeatureID={} successfully dispatched to storage subsystem.", featureId);
        } catch (Exception e) {
            log.error("❌ [DataService] Data ingest failed: {}", e.getMessage());
        }

        // 2. 🎯 真正的时空拓扑状态更新：拆解时间维度，核对地理栅格位图
        List<TSState> stateUpdates = new ArrayList<>();
        Instant baseTime = dataBlock.getTOrigin();
        TimeAxis tAxis = dataBlock.getTAxis();

        // 提取物理维度信息
        int tCount = (tAxis != null && tAxis.getCount() != null && tAxis.getCount() > 0) ? tAxis.getCount() : 1;
        long tResSec = getAxisResolutionInSeconds(tAxis);

        int width = (dataBlock.getXAxis() != null && dataBlock.getXAxis().getCount() != null) ? dataBlock.getXAxis().getCount() : 1;
        int height = (dataBlock.getYAxis() != null && dataBlock.getYAxis().getCount() != null) ? dataBlock.getYAxis().getCount() : 1;
        int frameSize = width * height;

        float[] rawData = dataBlock.getData();

        // 逐时间切片扫描
        for (int t = 0; t < tCount; t++) {
            Instant currentTime = baseTime.plusSeconds(t * tResSec);

            // 构造严格包含地理原点和坐标轴的单时刻空间壳子
            TSShell spatialShell = new TSShell.Builder(featureId)
                    .time(currentTime, null) // 时间退化为当前瞬时点
                    .x(dataBlock.getXOrigin(), dataBlock.getXAxis())
                    .y(dataBlock.getYOrigin(), dataBlock.getYAxis())
                    .z(dataBlock.getZOrigin(), dataBlock.getZAxis())
                    .build();

            // 生成全空的初始画板 (DataState.WAITING 会默认所有位图全为 false)
            TSState timeSliceState = TSShellFactory.createTSStateFromShell(spatialShell, DataState.WAITING);
            BitSet readyMask = timeSliceState.getReadyMask();

            // 定位当前时间切片在一维数组中的起始偏移量
            int offset = t * frameSize;
            boolean hasValidPixel = false;

            if (rawData != null && offset + frameSize <= rawData.length) {
                // 遍历当前帧的所有栅格点位
                for (int i = 0; i < frameSize; i++) {
                    // ⚠️ 真实的地理计算验证：只有非 NaN (已计算) 的栅格，才在状态位图中点亮
                    if (!Float.isNaN(rawData[offset + i])) {
                        readyMask.set(i);
                        hasValidPixel = true;
                    }
                }
            } else {
                // 异常兜底：如果没有数组，默认认为壳子范围内的所有点位均为合法产出
                readyMask.set(0, frameSize);
                hasValidPixel = true;
            }

            // 只有当这一帧真的输出了有效栅格数据时，才向上游发送状态更新
            if (hasValidPixel) {
                stateUpdates.add(timeSliceState);
            }
        }

        // 3. 触发器大盘的即时联动
        if (!stateUpdates.isEmpty()) {
            log.info("✅ [DataService] Unpacked multi-timestep output into {} rigorous spatial-temporal TSStates.", stateUpdates.size());
            eventPublisher.publishEvent(new DataStateUpdateEvent(this, stateUpdates, false));
        } else {
            log.warn("⚠️ [DataService] DataBlock contained no valid computational outputs (all NaN). No state update dispatched.");
        }
    }

    @Override
    public TSDataBlock fetchData(TSShell requirementShell) {
        try {
            return restTemplate.postForObject(SUBSYSTEM_FETCH_URL, requirementShell, TSDataBlock.class);
        } catch (Exception e) {
            log.error("❌ [DataService] Fetch data failed for FeatureID={}, Time={}. Error: {}",
                    requirementShell.getFeatureId(), requirementShell.getTOrigin(), e.getMessage());
            return null;
        }
    }

    // =================================================================
    // 辅助方法：时间单位转秒
    // =================================================================
    private long getAxisResolutionInSeconds(TimeAxis tAxis) {
        if (tAxis == null || tAxis.getResolution() == null) return 0;
        double res = tAxis.getResolution();
        String unit = (tAxis.getUnit() != null) ? tAxis.getUnit().trim().toLowerCase() : "s";
        if (unit.startsWith("h")) return (long) (res * 3600);
        if (unit.startsWith("m")) return (long) (res * 60);
        return (long) res;
    }
}