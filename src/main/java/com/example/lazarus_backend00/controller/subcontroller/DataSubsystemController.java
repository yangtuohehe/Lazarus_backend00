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

@RestController
@RequestMapping("/api/v1/data-subsystem")
public class DataSubsystemController {

    private static final Logger log = LoggerFactory.getLogger(DataSubsystemController.class);

    private final DataSubsystemServiceImpl subsystemService;
    private final DataStorageServiceImpl storageService;

    public DataSubsystemController(DataSubsystemServiceImpl subsystemService,
                                   DataStorageServiceImpl storageService) {
        this.subsystemService = subsystemService;
        this.storageService = storageService;
    }

    @PostMapping("/sync")
    public String syncData(@RequestParam("time") Instant time) {
        log.info("📡 [API] Sync request received for {}", time);
        subsystemService.executeIngestion(time);
        return "ACK: Sync Triggered for " + time;
    }

    @PostMapping("/status/check")
    public ResponseEntity<List<DataCheckResult>> checkDataStatus(@RequestBody List<TSShell> shells) {
        List<DataCheckResult> results = storageService.checkDataStatus(shells);
        return ResponseEntity.ok(results);
    }

    // 🔥 协议变更：模型主系统发来一个壳子，我们返回一个实心数据块
    @PostMapping("/data/fetch")
    public ResponseEntity<TSDataBlock> fetchData(@RequestBody TSShell shell) {
        TSDataBlock dataBlock = storageService.fetchSingleDataBlock(shell);
        if (dataBlock == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dataBlock);
    }

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
}