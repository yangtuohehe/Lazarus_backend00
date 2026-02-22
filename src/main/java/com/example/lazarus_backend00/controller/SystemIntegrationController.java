package com.example.lazarus_backend00.controller;

import com.example.lazarus_backend00.dto.subdto.DataUpdatePacket;
import com.example.lazarus_backend00.service.DataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 主系统集成接口 (System Integration API)
 * 职责：接收外部子系统的 Webhook/HTTP 通知
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
    public String onBatchDataArrival(@RequestBody List<DataUpdatePacket> packets) {
        // 交给 DataService 处理
        dataService.notifyDataArrivals(packets);
        return "ACK";
    }
}
