package com.example.lazarus_backend00.controller;

import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.dto.TaskStatusDTO;
import com.example.lazarus_backend00.dto.subdto.DataUpdatePacket;
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
    // 方向：数据子系统 -> 模型主系统
    // 作用：接收数据子系统发来的数据就绪通知，触发后台异步事件状态机。
    // ========================================================================
    @PostMapping("/notify-batch")
    public ResponseEntity<String> onDataUpdateNotification(@RequestBody List<DataUpdatePacket> packets) {
        log.info("📡 [接口1-Notify] 收到数据变更批量通知, 数量: {}", packets.size());

        if (packets == null || packets.isEmpty()) {
            return ResponseEntity.badRequest().body("Notification packets are empty");
        }

        // 触发数据核心枢纽进行处理 (内部会调用 Trigger 进行状态机更新与任务生成)
        dataService.notifyDataArrivals(packets);

        return ResponseEntity.ok("ACK: Batch update signals received.");
    }

    // ========================================================================
    // 接口 2：获取活跃任务列表 (供前端看板展示)
    // 方向：前端看板 -> 模型主系统
    // 作用：获取当前正在排队、取数、计算中的任务状态列表。
    // ========================================================================
    @GetMapping("/tasks/active")
    public ResponseEntity<List<TaskStatusDTO>> getActiveTasks() {
        // 直接返回我们之前在 Orchestrator 中维护的 activeTasksMap 的值
        return ResponseEntity.ok(orchestratorService.getActiveTasks());
    }

    // ========================================================================
    // 接口 3：手动拉取数据测试接口 (可选调试用)
    // 方向：调试端 -> 模型主系统 -> 数据子系统
    // ========================================================================
    @PostMapping("/fetch")
    public ResponseEntity<TSDataBlock> fetchData(@RequestBody TSShell shell) {
        log.info("🔍 [接口3-Fetch] 手动查询数据测试: Feature={}, Time={}",
                shell.getFeatureId(), shell.getTOrigin());

        try {
            // 调用 DataService (它充当 Client 向数据端发起 HTTP 请求)
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
    // 接口 4：手动推送数据测试接口 (可选调试用)
    // 方向：调试端 -> 模型主系统 -> 数据子系统 (附带级联触发)
    // ========================================================================
    @PostMapping("/push")
    public ResponseEntity<String> pushData(@RequestBody TSDataBlock block) {
        log.info("💾 [接口4-Push] 手动推送数据测试: Feature={}", block.getFeatureId());

        try {
            // 调用 DataService 将数据回写到数据子系统，并触发下游的级联更新
            dataService.pushData(block.getFeatureId(), block);

            return ResponseEntity.ok("ACK: Data pushed successfully.");

        } catch (Exception e) {
            log.error("Push failed", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}