package com.example.lazarus_backend00.controller.subcontroller;

import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.dto.subdto.DataCheckResult;
import com.example.lazarus_backend00.service.subservice.DataStorageServiceImpl;
import com.example.lazarus_backend00.service.subservice.DataSubsystemServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * 数据子系统控制器
 * 职责：
 * 1. 暴露数据查询接口 (Fetch/Check) -> 委托给 DataStorageService
 * 2. 暴露仿真数据回写接口 (Ingest-Calc) -> 委托给 DataStorageService
 * 3. 暴露实测数据生成接口 (Sync) -> 委托给 DataSubsystemService
 */
@RestController
@RequestMapping("/api/v1/data-subsystem")
public class DataSubsystemController {

    private static final Logger log = LoggerFactory.getLogger(DataSubsystemController.class);

    // 负责仿真调度 (生成实测数据)
    private final DataSubsystemServiceImpl subsystemService;

    // 负责数据存取 (查询、回写计算结果)
    private final DataStorageServiceImpl storageService;

    public DataSubsystemController(DataSubsystemServiceImpl subsystemService,
                                   DataStorageServiceImpl storageService) {
        this.subsystemService = subsystemService;
        this.storageService = storageService;
    }

    // =================================================================
    // 1. 实测数据生成 (Sync) -> DataSubsystemService
    // =================================================================
    /**
     * 仿真同步触发器
     * 外部调用此接口，通知搬运工生成/搬运实测数据到实时库。
     */
    @PostMapping("/sync")
    public String syncData(@RequestParam("time") Instant time) {
        log.info("📡 [API] Sync request received for {}", time);
        subsystemService.executeIngestion(time);
        return "ACK: Sync Triggered for " + time;
    }

    // =================================================================
    // 2. 数据查询与获取 -> DataStorageService
    // =================================================================
    /**
     * 状态检查
     * 返回 0(缺失), 1(实测), 2(模拟)
     */
    @PostMapping("/status/check")
    public ResponseEntity<List<DataCheckResult>> checkDataStatus(@RequestBody List<TSShell> shells) {
        List<DataCheckResult> results = storageService.checkDataStatus(shells);
        return ResponseEntity.ok(results);
    }

    /**
     * 数据读取
     * 优先读取实测数据，无实测则读取模拟数据
     */
    @PostMapping("/data/fetch")
    public ResponseEntity<List<TSDataBlock>> fetchData(@RequestBody List<TSShell> shells) {
        List<TSDataBlock> dataBlocks = storageService.fetchDataBlocks(shells);
        return ResponseEntity.ok(dataBlocks);
    }

    // =================================================================
    // 3. 数据入库 -> DataStorageService
    // =================================================================

    /**
     * [计算结果入库]
     * 接收模型算出来的结果，强制加 -ls 后缀保存。
     */
    @PostMapping("/data/ingest-calc")
    public ResponseEntity<String> ingestCalculatedData(@RequestBody TSDataBlock dataBlock) {
        try {
            storageService.ingestCalculatedData(dataBlock);
            return ResponseEntity.ok("ACK: Calculated Data saved with '-ls'.");
        } catch (Exception e) {
            log.error("Ingest Calc Error", e);
            return ResponseEntity.internalServerError().body("ERR: " + e.getMessage());
        }
    }

    /**
     * [实测数据入库 - 已禁用]
     * 接口签名保持不变，但功能已由 /sync 内部接管。
     * 返回 405 Method Not Allowed 提示调用者。
     */
    @PostMapping("/data/ingest")
    public ResponseEntity<String> ingestData(@RequestBody TSDataBlock dataBlock) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body("ERR: External ingestion of measured data is disabled. Please use /sync to generate measured data internally.");
    }
}