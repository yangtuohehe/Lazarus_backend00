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

    // 内存中的 Future 缓存池
    // Key: TaskID, Value: 数据结果的 Future
    private final Map<Long, CompletableFuture<List<TSDataBlock>>> futureCache = new ConcurrentHashMap<>();

    public DataPreloadServiceImpl(DataService dataService) {
        this.dataService = dataService;
    }

    @Override
    @Async("taskExecutor") // 关键：异步执行
    public void startFetching(long taskId, List<TSShell> inputShells) {
        // 1. 注册占位符
        CompletableFuture<List<TSDataBlock>> future = new CompletableFuture<>();
        futureCache.put(taskId, future);

        try {
            // log.debug("📥 [Preload] Task-{} 开始从数据库拉取 {} 个数据块...", taskId, inputShells.size());

            List<TSDataBlock> resultBlocks = new ArrayList<>();

            // 2. 执行 IO 操作 (调用 DataService)
            for (TSShell shell : inputShells) {
                // 假设 fetchData 返回单个 Block
                TSDataBlock block = dataService.fetchData(shell);

                // 数据完整性检查
                if (block == null) {
                    throw new IllegalStateException("缺失必要输入数据: FeatureID=" + shell.getFeatureId());
                }
                resultBlocks.add(block);
            }

            // 3. 填充结果，唤醒等待线程
            future.complete(resultBlocks);
            // log.debug("✅ [Preload] Task-{} 数据准备就绪。", taskId);

        } catch (Exception e) {
            // 异常传递：通知等待者发生了错误
            log.error("❌ [Preload] Task-{} 取数失败: {}", taskId, e.getMessage());
            future.completeExceptionally(e);
        }
    }

    @Override
    public List<TSDataBlock> getData(long taskId, long timeoutSeconds) throws Exception {
        CompletableFuture<List<TSDataBlock>> future = futureCache.get(taskId);

        if (future == null) {
            throw new IllegalStateException("Task-" + taskId + " 未注册预取任务，流程逻辑错误！");
        }

        try {
            // 4. 阻塞等待
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new TimeoutException("等待数据超时: Task-" + taskId);
        } catch (ExecutionException e) {
            // 抛出 startFetching 中捕获的原始异常
            throw (Exception) e.getCause();
        } finally {
            // 5. 清理缓存 (一次性消费)
            futureCache.remove(taskId);
        }
    }
}