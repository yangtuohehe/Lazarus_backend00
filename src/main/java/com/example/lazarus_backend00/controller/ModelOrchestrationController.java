package com.example.lazarus_backend00.controller;

import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.domain.data.TSState;
import com.example.lazarus_backend00.dto.TaskStatusDTO;
import com.example.lazarus_backend00.service.DataService;
import com.example.lazarus_backend00.service.ModelOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型编排与系统集成主控接口 (Model Orchestration & System Integration API)
 * 职责：接收外部数字孪生主控系统的推送（Webhook）、管控模型运行编排、以及数据拉取与推送。
 */
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
    // 接口 1：系统集成与批量数据更新通知接口 (Webhook / Notification)
    // 🔥 统一入口：直接接收 List<TSState>，彻底抛弃冗余的 Controller 和 DTO
    // ========================================================================
    @PostMapping("/notify-batch")
    public ResponseEntity<String> onDataUpdateNotification(@RequestBody List<TSState> incomingStates) {

        if (incomingStates == null || incomingStates.isEmpty()) {
            return ResponseEntity.badRequest().body("REJECTED: Notification states are empty or invalid");
        }

        log.info("📡 [Controller] 收到来自数字孪生主系统的 {} 个 TSState 状态广播包", incomingStates.size());

        // 直接将标准协议包转交给数据服务处理（它将负责把这些状态推入事件总线，唤醒调度引擎）
        dataService.notifyDataArrivals(incomingStates);

        return ResponseEntity.ok("ACK: Batch TSState update signals received and published.");
    }

    // ========================================================================
    // 接口 2：获取活跃任务列表 (供前端看板或外部系统监控展示)
    // ========================================================================
    @GetMapping("/tasks/active")
    public ResponseEntity<List<TaskStatusDTO>> getActiveTasks() {
        return ResponseEntity.ok(orchestratorService.getActiveTasks());
    }

    // ========================================================================
    // 接口 3：模型向数据端发起查数请求 (Fetch) - 契约：TSShell
    // ========================================================================
    @PostMapping("/fetch")
    public ResponseEntity<TSDataBlock> fetchData(@RequestBody TSShell shell) {
        log.info("🔍 [接口3-Fetch] 向数据层查询真实时空张量数据: Feature={}, Time={}",
                shell.getFeatureId(), shell.getTOrigin());

        try {
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
    // 接口 4：模型将计算结果存回数据端 (Push) - 契约：TSDataBlock
    // ========================================================================
    @PostMapping("/push")
    public ResponseEntity<String> pushData(@RequestBody TSDataBlock block) {
        log.info("💾 [接口4-Push] 推送计算产物至数据层: Feature={}", block.getFeatureId());

        try {
            dataService.pushData(block.getFeatureId(), block);
            return ResponseEntity.ok("ACK: Data pushed successfully.");
        } catch (Exception e) {
            log.error("Push failed", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}