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
    // 接口 1：批量数据更新通知接口 (Data Update Notification)
    // 🔥 终极形态：直接接收 List<TSState>，彻底抛弃 DataUpdatePacket 和 JsonNode 解析
    // ========================================================================
    @PostMapping("/notify-batch")
    public ResponseEntity<String> onDataUpdateNotification(@RequestBody List<TSState> incomingStates) {

        if (incomingStates == null || incomingStates.isEmpty()) {
            return ResponseEntity.badRequest().body("Notification states are empty or invalid");
        }

        log.info("📡 [Controller] 收到来自数据端的 {} 个 TSState 状态广播包", incomingStates.size());

        // 直接将标准协议包转交给数据服务处理（它将负责把这些状态推入事件总线）
        dataService.notifyDataArrivals(incomingStates);

        return ResponseEntity.ok("ACK: Batch TSState update signals received and published.");
    }

    // ========================================================================
    // 接口 2：获取活跃任务列表 (供前端看板展示)
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
        log.info("🔍 [接口3-Fetch] 向数据端查询真实数据: Feature={}, Time={}",
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
        log.info("💾 [接口4-Push] 推送计算产物至数据端: Feature={}", block.getFeatureId());

        try {
            dataService.pushData(block.getFeatureId(), block);
            return ResponseEntity.ok("ACK: Data pushed successfully.");
        } catch (Exception e) {
            log.error("Push failed", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}