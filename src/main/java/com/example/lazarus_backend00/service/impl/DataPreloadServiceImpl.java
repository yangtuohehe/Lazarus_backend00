package com.example.lazarus_backend00.service.impl;

import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.service.DataPreloadService;
import com.example.lazarus_backend00.service.DataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class DataPreloadServiceImpl implements DataPreloadService {

    private static final Logger log = LoggerFactory.getLogger(DataPreloadServiceImpl.class);

    private final DataService dataService;

    /**
     * Future cache pool in memory
     * Key: TaskID, Value: CompletableFuture for data results
     */
    private final Map<Long, CompletableFuture<List<TSDataBlock>>> futureCache = new ConcurrentHashMap<>();

    public DataPreloadServiceImpl(DataService dataService) {
        this.dataService = dataService;
    }

    @Override

    public void startFetching(long taskId, List<TSShell> inputShells) {
        // 1. Register placeholder
        CompletableFuture<List<TSDataBlock>> future = new CompletableFuture<>();
        futureCache.put(taskId, future);

        try {
            log.info("📥 [Preload] Task-{} started. Fetching {} blocks from database...", taskId, inputShells.size());

            List<TSDataBlock> resultBlocks = new ArrayList<>();

            // 2. Perform IO operations (Calling DataService)
            for (TSShell shell : inputShells) {
                TSDataBlock block = dataService.fetchData(shell);

                // Check data integrity
                if (block == null) {
                    throw new IllegalStateException("Required input data missing: FeatureID=" + shell.getFeatureId()
                            + " at " + shell.getTOrigin());
                }
                resultBlocks.add(block);
            }

            // 3. Complete future and wake up waiting threads
            future.complete(resultBlocks);
            log.info("✅ [Preload] Task-{} data preparation ready.", taskId);

        } catch (Exception e) {
            // Error propagation: Notify awaiters that an error occurred
            log.error("❌ [Preload] Task-{} fetch failed: {}", taskId, e.getMessage());
            future.completeExceptionally(e);
        }
    }

    @Override
    public List<TSDataBlock> getData(long taskId, long timeoutSeconds) throws Exception {
        CompletableFuture<List<TSDataBlock>> future = futureCache.get(taskId);

        if (future == null) {
            // This happens if getData is called before startFetching registers the ID
            throw new IllegalStateException("CRITICAL: Task-" + taskId + " not found in Preload Registry!");
        }

        try {
            // 4. Block and wait for results
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("⏰ [Preload] Wait timeout: Task-{}", taskId);
            throw new TimeoutException("Wait data timeout: Task-" + taskId);
        } catch (ExecutionException e) {
            // Throw the original exception caught in startFetching
            log.error("❌ [Preload] Execution exception for Task-{}: {}", taskId, e.getCause().getMessage());
            throw (Exception) e.getCause();
        } finally {
            // 5. Clean cache (One-time consumption)
            futureCache.remove(taskId);
        }
    }
}