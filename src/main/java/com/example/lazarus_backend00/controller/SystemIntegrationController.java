package com.example.lazarus_backend00.controller;

import com.example.lazarus_backend00.domain.data.TSState;
import com.example.lazarus_backend00.service.DataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 主系统集成接口 (System Integration API)
 * 职责：接收外部子系统的 Webhook/HTTP 通知
 * 协议：全系统统一使用 TSState 像元级位图协议，彻底废弃冗余 DTO
 */
@RestController
@RequestMapping("/api/v1/system/integration")
public class SystemIntegrationController {

    private final DataService dataService;

    public SystemIntegrationController(DataService dataService) {
        this.dataService = dataService;
    }

    /**
     * [Webhook] 接收批量数据更新通知
     * URL: POST /api/v1/system/integration/notify-batch
     */
    @PostMapping("/notify-batch")
    public String onBatchDataArrival(@RequestBody List<TSState> incomingStates) {

        // 增加基础的容错校验
        if (incomingStates == null || incomingStates.isEmpty()) {
            return "REJECTED: Empty state list.";
        }

        // 没有任何拆包、转包的废话代码，直接交给 DataService 处理！
        // DataService 会原封不动地把它们扔进事件总线，唤醒模型触发器。
        dataService.notifyDataArrivals(incomingStates);

        return "ACK: Batch TSState update signals received.";
    }
}