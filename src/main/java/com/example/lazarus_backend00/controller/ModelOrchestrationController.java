package com.example.lazarus_backend00.controller;

import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.service.DataService;
import com.example.lazarus_backend00.service.ModelOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/orchestration")
public class ModelOrchestrationController {

    private static final Logger log = LoggerFactory.getLogger(ModelOrchestrationController.class);

    private final ModelOrchestratorService orchestratorService;
    private final DataService dataService;

    public ModelOrchestrationController(ModelOrchestratorService orchestratorService,
                                        DataService dataService) {
        this.orchestratorService = orchestratorService;
        this.dataService = dataService;
    }

    // ========================================================================
    // 接口 1：数据更新通知接口 (Data Update Notification)
    // 方向：数据子系统 -> 模型子系统
    // 作用：仅仅通知数据更新，系统立即应答，随后后台异步执行任务编排。
    // ========================================================================
    @PostMapping("/notify")
    public ResponseEntity<String> onDataUpdateNotification(@RequestBody DataUpdateNotificationDto notification) {
        log.info("📡 [接口1-Notify] 收到数据变更通知: Feature={}, Time={}",
                notification.getFeatureId(), notification.getEndTime());

        // 1. 基础校验
        if (notification.getFeatureId() == null || notification.getEndTime() == null) {
            return ResponseEntity.badRequest().body("Notification incomplete");
        }

        // 2. 触发后台编排 (异步执行，不会阻塞接口返回)
        // 注意：DataService/Preloader 后续会利用接口2去拉取真正的数据
        orchestratorService.onDataChanged(
                notification.getFeatureId(),
                notification.getStartTime(),
                notification.getEndTime()
        );

        // 3. 立即应答
        return ResponseEntity.ok("ACK: Update signal received.");
    }

    // ========================================================================
    // 接口 2：数据查询接口 (Data Query)
    // 方向：模型子系统 (或调试端) -> 数据子系统
    // 作用：发送 TSShell (作为查询条件)，返回 TSDataBlock (包含真实数据)。
    // 本接口充当 Proxy，实际逻辑委托给 DataService 去调用数据端。
    // ========================================================================
    @PostMapping("/fetch")
    public ResponseEntity<TSDataBlock> fetchData(@RequestBody TSShell shell) {
        log.info("🔍 [接口2-Fetch] 查询数据: Feature={}, Time={}",
                shell.getFeatureId(), shell.getTOrigin());

        try {
            // 调用 DataService (它充当 Client 向数据端发起请求)
            TSDataBlock block = dataService.fetchData(shell);

            if (block == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(block);

        } catch (Exception e) {
            log.error("Fetch failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========================================================================
    // 接口 3：写入接口 (Data Write)
    // 方向：模型子系统 -> 数据子系统
    // 作用：向数据端传输计算完成的 TSDataBlock。
    // 本接口充当 Proxy，实际逻辑委托给 DataService 去推送数据。
    // ========================================================================
    @PostMapping("/push")
    public ResponseEntity<String> pushData(@RequestBody TSDataBlock block) {
        log.info("💾 [接口3-Push] 推送数据: Feature={}, GridSize={}",
                block.getFeatureId(), block.getData().length);

        try {
            // 调用 DataService 将数据回写到数据子系统
            dataService.pushData(block.getFeatureId(), block);

            return ResponseEntity.ok("ACK: Data pushed successfully.");

        } catch (Exception e) {
            log.error("Push failed", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // ================== DTO 定义 (内部类或独立文件) ==================

    /**
     * 专门用于接口1的轻量级通知对象
     */
    public static class DataUpdateNotificationDto {
        private Integer featureId;
        private Instant startTime;
        private Instant endTime;

        // Getters & Setters
        public Integer getFeatureId() { return featureId; }
        public void setFeatureId(Integer featureId) { this.featureId = featureId; }
        public Instant getStartTime() { return startTime; }
        public void setStartTime(Instant startTime) { this.startTime = startTime; }
        public Instant getEndTime() { return endTime; }
        public void setEndTime(Instant endTime) { this.endTime = endTime; }
    }
}