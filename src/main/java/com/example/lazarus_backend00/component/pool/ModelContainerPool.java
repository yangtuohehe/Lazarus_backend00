package com.example.lazarus_backend00.component.pool;

import com.example.lazarus_backend00.component.container.ContainerStatus;
import com.example.lazarus_backend00.component.container.ModelContainer;
import com.example.lazarus_backend00.component.orchestration.ModelEventTrigger;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.infrastructure.config.ModelPoolConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 模型容器池 (Eager Loading + LRU Eviction 版)
 * 特性：
 * 1. 注册即加载 (Eager Load)。
 * 2. 内存不足时，自动卸载最久未使用的闲置容器 (LRU)。
 * 3. 适配单特征输入 List<List<TSDataBlock>>。
 */
@Component
public class ModelContainerPool {

    // ================== 核心组件 ==================
    private final Map<Integer, ModelContainer> pool = new ConcurrentHashMap<>();

    // 🔥 新增：LRU 队列 (存储 RuntimeID)，队头是最久未使用的，队尾是最近使用的
    private final Deque<Integer> lruQueue = new ConcurrentLinkedDeque<>();

    private final ModelEventTrigger eventTrigger;
    private final ModelPoolConfig config;

    // ================== 资源守卫 ==================
    private final Object cpuMonitor = new Object();
    private int currentRunningCount = 0;

    private final Object memoryMonitor = new Object();
    private volatile long currentUsedMemoryMB = 0; // 当前已用显存/内存

    public ModelContainerPool(ModelEventTrigger eventTrigger, ModelPoolConfig config) {
        this.eventTrigger = eventTrigger;
        this.config = config;
    }

    @PostConstruct
    public void init() {
        System.out.println("====== [ModelContainerPool] 初始化 (Eager + LRU) ======");
        System.out.println("   - Max Concurrency: " + config.getMaxConcurrentRuns());
        System.out.println("   - Max Memory: " + config.getMaxMemoryMb() + " MB");
    }

    // ================== 1. 注册与预加载 (Eager Loading) ==================

    public void registerContainer(int runtimeId, ModelContainer container) {
        // 1. 清理旧实例 (如果 ID 冲突)
        if (pool.containsKey(runtimeId)) {
            unregisterContainer(runtimeId);
        }

        System.out.println(">>> [容器池] 收到注册请求: 模型 " + runtimeId + "，正在试探性加载...");

        // 2. 试探性加载 (Load Probe)
        // 注意：此时尚未放入 pool，如果加载失败或者内存不够，它会被 GC 回收
        boolean loadSuccess = container.load();

        if (!loadSuccess) {
            System.err.println("❌ [注册失败] 模型 " + runtimeId + " 物理文件加载失败，拒绝注册。");
            return; // 直接结束，不入池
        }

        // 3. 获取真实内存 (此时已 Load，数值准确)
        long realMemoryNeeded = container.getMemoryUsage();

        // 4. 内存准入控制 (Admission Control)
        boolean admissionGranted = false;

        synchronized (memoryMonitor) {
            // A. 如果加上这个新模型会超标，先尝试踢掉闲置模型
            if (currentUsedMemoryMB + realMemoryNeeded > config.getMaxMemoryMb()) {
                tryEvictToMakeSpace(realMemoryNeeded);
            }

            // B. 二次检查：现在空间够了吗？
            if (currentUsedMemoryMB + realMemoryNeeded <= config.getMaxMemoryMb()) {
                // ✅ 准入通过
                admissionGranted = true;

                // 正式入池
                pool.put(runtimeId, container);

                // 提交内存占用
                currentUsedMemoryMB += realMemoryNeeded;

                // 加入 LRU 活跃队列
                lruQueue.offerLast(runtimeId);

                System.out.println("✅ [注册成功] 模型 " + runtimeId + " 已就绪。占用: " + realMemoryNeeded + "MB, 池水位: " + currentUsedMemoryMB + "MB");
            } else {
                // ❌ 准入拒绝
                System.err.println("⛔ [注册拒绝] 内存严重不足！(需 " + realMemoryNeeded + "MB, 即使淘汰闲置模型后仍不足)");
                admissionGranted = false;
            }
        }

        // 5. 根据准入结果处理
        if (admissionGranted) {
            // 只有成功入池的模型，才注册事件触发器
            eventTrigger.registerModel(runtimeId, container.getParameterList(), Duration.ofHours(1));
        } else {
            // 拒绝入池：立即卸载，释放刚才试探性加载占用的物理内存
            container.unload();
            // 注意：这里不需要 pool.remove，因为我们在判定成功前根本没 put 进去
            // 该 container 对象随即变成垃圾对象，等待 GC
        }
    }

    public void unregisterContainer(int runtimeId) {
        ModelContainer container = pool.get(runtimeId);
        if (container != null) {
            synchronized (memoryMonitor) {
                if (container.getStatus() == ContainerStatus.LOADED) {
                    currentUsedMemoryMB -= container.getMemoryUsage();
                }
                container.unload();
            }
            pool.remove(runtimeId);
            lruQueue.remove(runtimeId); // 从 LRU 移除
            eventTrigger.unregisterModel(runtimeId);
        }
    }

    // ================== 2. 核心运行接口 (Execution) ==================

    /**
     * 执行模型推理
     * 修改点：
     * 1. 入参改为 List<List<TSDataBlock>> 适配单特征
     * 2. 增加 LRU 更新逻辑
     * 3. 增加 "被淘汰后重载" 逻辑
     */
    public List<List<TSDataBlock>> executeModel(int runtimeId, List<List<TSDataBlock>> inputGroups) {
        ModelContainer container = pool.get(runtimeId);
        if (container == null) throw new IllegalArgumentException("模型未注册: " + runtimeId);

        long timeoutMillis = config.getWaitTimeoutSeconds() * 1000L;
        long startTime = System.currentTimeMillis();

        try {
            // ------------------------------------------------
            // Step 1: 申请 CPU
            // ------------------------------------------------
            synchronized (cpuMonitor) {
                while (currentRunningCount >= config.getMaxConcurrentRuns()) {
                    long wait = timeoutMillis - (System.currentTimeMillis() - startTime);
                    if (wait <= 0) throw new RuntimeException("CPU 排队超时");
                    cpuMonitor.wait(wait);
                }
                currentRunningCount++;
            }

            // ------------------------------------------------
            // Step 2: 检查加载状态 (内存处理)
            // ------------------------------------------------
            synchronized (container) {
                // 更新 LRU：既然要运行了，把它移到队尾 (最新)
                updateLruPosition(runtimeId);

                // 如果模型之前没加载成功，或者被 LRU 淘汰了(UNLOADED)，现在必须重载
                if (container.getStatus() != ContainerStatus.LOADED &&
                        container.getStatus() != ContainerStatus.RUNNING) {

                    System.out.println("🔄 [重载] 模型 " + runtimeId + " 之前被卸载或未加载，正在恢复...");
                    long needed = container.getMemoryUsage();

                    synchronized (memoryMonitor) {
                        // 再次尝试腾挪空间
                        if (currentUsedMemoryMB + needed > config.getMaxMemoryMb()) {
                            tryEvictToMakeSpace(needed);
                        }

                        // 如果腾挪后还不够，就只能死等了
                        while (currentUsedMemoryMB + needed > config.getMaxMemoryMb()) {
                            long wait = timeoutMillis - (System.currentTimeMillis() - startTime);
                            if (wait <= 0) throw new RuntimeException("内存排队超时 (无法腾出 " + needed + "MB)");
                            System.out.println("⏳ [排队] 等待内存释放...");
                            memoryMonitor.wait(wait);
                        }

                        // 额度够了，执行加载
                        if (container.load()) {
                            currentUsedMemoryMB += needed;
                        } else {
                            throw new RuntimeException("模型加载失败");
                        }
                    }
                }
            }

            // ------------------------------------------------
            // Step 3: 执行推理
            // ------------------------------------------------
            System.out.println("🚀 [执行] 模型 " + runtimeId + " Running...");

            // 🔥 核心修改：调用新的 run 接口
            return container.run(inputGroups);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("被中断", e);
        } catch (Exception e) {
            System.err.println("❌ 模型 " + runtimeId + " 执行异常: " + e.getMessage());
            throw e;
        } finally {
            // ------------------------------------------------
            // Step 4: 归还资源
            // ------------------------------------------------
            synchronized (cpuMonitor) {
                currentRunningCount--;
                cpuMonitor.notifyAll();
            }
            // 注意：内存不需要归还，因为我们现在是 Eager Mode，
            // 只有被 Evict 时才归还内存，跑完不卸载。
            synchronized (memoryMonitor) {
                memoryMonitor.notifyAll(); // 通知那些在等内存的
            }
        }
    }

    // ================== 3. 内存回收算法 (LRU Eviction) ==================

    /**
     * 尝试卸载闲置模型，直到腾出 targetNeeded 内存
     * @param targetNeeded 需要腾出的空间
     */
    private void tryEvictToMakeSpace(long targetNeeded) {
        long freed = 0;
        long maxMem = config.getMaxMemoryMb();

        // 只要空间不够，且 LRU 队列里还有人，就尝试踢
        // 注意：这里我们只遍历，不 remove，确定要踢了再 remove
        Iterator<Integer> it = lruQueue.iterator();

        List<Integer> toEvict = new ArrayList<>();

        while (it.hasNext() && (currentUsedMemoryMB - freed + targetNeeded > maxMem)) {
            Integer candidateId = it.next();
            ModelContainer candidate = pool.get(candidateId);

            if (candidate == null) continue;

            // 🔥 关键：绝对不能踢正在运行的模型
            if (candidate.getStatus() != ContainerStatus.RUNNING) {
                freed += candidate.getMemoryUsage();
                toEvict.add(candidateId);
            }
        }

        // 执行卸载
        for (Integer id : toEvict) {
            ModelContainer c = pool.get(id);
            if (c != null) {
                c.unload(); // 释放物理内存
                lruQueue.remove(id); // 从 LRU 移除
                currentUsedMemoryMB -= c.getMemoryUsage();
                System.out.println("♻️ [淘汰] 内存不足，卸载闲置模型: " + id);
            }
        }
    }

    /**
     * 更新 LRU 位置 (最近使用)
     * 将 ID 移到队尾
     */
    private void updateLruPosition(int runtimeId) {
        // ConcurrentLinkedDeque remove 比较慢 (O(N))，但在内存吃紧的场景下可以接受
        // 如果追求极致性能，需配合 ConcurrentHashMap<Id, Node> 实现双向链表
        lruQueue.remove(runtimeId);
        lruQueue.offerLast(runtimeId);
    }

    // ================== Getters ==================
    public int size() { return pool.size(); }
    public long getCurrentUsedMemoryMB() { return currentUsedMemoryMB; }
    /**
     * 获取指定运行时 ID 的容器实例
     * 注意：返回的可能是 null
     */
    public ModelContainer getContainer(int runtimeId) {
        return pool.get(runtimeId);
    }

    /**
     * 获取所有已注册的容器 (用于监控/列表展示)
     */
    public Map<Integer, ModelContainer> getAllContainers() {
        return Collections.unmodifiableMap(pool);
    }
}