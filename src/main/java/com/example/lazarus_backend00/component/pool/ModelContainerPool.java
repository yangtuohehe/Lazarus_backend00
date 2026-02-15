package com.example.lazarus_backend00.component.pool;

import com.example.lazarus_backend00.component.container.ContainerStatus;
import com.example.lazarus_backend00.component.container.ModelContainer;
import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.component.orchestration.ModelEventTrigger;
import com.example.lazarus_backend00.domain.axis.Axis;
import com.example.lazarus_backend00.domain.axis.TimeAxis;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.infrastructure.config.ModelPoolConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class ModelContainerPool {

    // ================== 核心组件 ==================
    private final Map<Integer, ModelContainer> pool = new ConcurrentHashMap<>();
    private final Deque<Integer> lruQueue = new ConcurrentLinkedDeque<>();
    private final ModelEventTrigger eventTrigger;
    private final ModelPoolConfig config;

    // ================== 资源守卫 ==================
    private final Object cpuMonitor = new Object();
    private int currentRunningCount = 0;
    private final Object memoryMonitor = new Object();
    private volatile long currentUsedMemoryMB = 0;

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
        if (pool.containsKey(runtimeId)) {
            unregisterContainer(runtimeId);
        }

        System.out.println(">>> [容器池] 收到注册请求: 模型 " + runtimeId + "，正在试探性加载...");

        if (!container.load()) {
            System.err.println("❌ [注册失败] 模型 " + runtimeId + " 物理文件加载失败，拒绝注册。");
            return;
        }

        long realMemoryNeeded = container.getMemoryUsage();
        boolean admissionGranted = false;

        synchronized (memoryMonitor) {
            if (currentUsedMemoryMB + realMemoryNeeded > config.getMaxMemoryMb()) {
                tryEvictToMakeSpace(realMemoryNeeded);
            }

            if (currentUsedMemoryMB + realMemoryNeeded <= config.getMaxMemoryMb()) {
                admissionGranted = true;
                pool.put(runtimeId, container);
                currentUsedMemoryMB += realMemoryNeeded;
                lruQueue.offerLast(runtimeId);
                System.out.println("✅ [注册成功] 模型 " + runtimeId + " 已就绪。占用: " + realMemoryNeeded + "MB");
            } else {
                System.err.println("⛔ [注册拒绝] 内存严重不足！(需 " + realMemoryNeeded + "MB)");
                admissionGranted = false;
            }
        }

        if (admissionGranted) {
            // 🔥🔥🔥 核心修改区：无需修改 ModelContainer 接口，直接从 Parameter 推导 🔥🔥🔥
            Duration derivedStep = Duration.ofHours(1);   // 默认值
            Duration derivedWindow = Duration.ofHours(1); // 默认值

            List<Parameter> params = container.getParameterList();
            if (params != null) {
                for (Parameter p : params) {
                    if (p.getAxisList() != null) {
                        for (Axis axis : p.getAxisList()) {
                            // 只要发现 TimeAxis，就提取信息 (通常模型的输入输出时间分辨率是一致的)
                            if (axis instanceof TimeAxis) {
                                // 假设 Axis 中的 resolution 单位是 "秒" (Double)
                                long resSeconds = Math.round(axis.getResolution());
                                long count = axis.getCount();

                                if (resSeconds > 0) {
                                    derivedStep = Duration.ofSeconds(resSeconds);
                                    // 窗口 = 步长 * 点数 (即总的时间跨度)
                                    derivedWindow = Duration.ofSeconds(resSeconds * count);
                                }
                                break; // 找到一个有效的时间轴即可跳出
                            }
                        }
                    }
                    // 如果已经从默认值变更了，说明找到了，不再遍历后续参数
                    if (!derivedStep.equals(Duration.ofHours(1))) break;
                }
            }

            // 注册到触发器
            eventTrigger.registerModel(
                    runtimeId,
                    params,
                    derivedStep,   // 自动推导的步长
                    derivedWindow  // 自动推导的窗口
            );

        } else {
            container.unload();
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
            lruQueue.remove(runtimeId);
            eventTrigger.unregisterModel(runtimeId);
        }
    }

    // ================== 2. 核心运行接口 ==================

    public List<List<TSDataBlock>> executeModel(int runtimeId, List<List<TSDataBlock>> inputGroups) {
        ModelContainer container = pool.get(runtimeId);
        if (container == null) throw new IllegalArgumentException("模型未注册: " + runtimeId);

        long timeoutMillis = config.getWaitTimeoutSeconds() * 1000L;
        long startTime = System.currentTimeMillis();

        try {
            // 1. CPU 锁
            synchronized (cpuMonitor) {
                while (currentRunningCount >= config.getMaxConcurrentRuns()) {
                    long wait = timeoutMillis - (System.currentTimeMillis() - startTime);
                    if (wait <= 0) throw new RuntimeException("CPU 排队超时");
                    cpuMonitor.wait(wait);
                }
                currentRunningCount++;
            }

            // 2. 内存锁 (支持重载)
            synchronized (container) {
                updateLruPosition(runtimeId);

                if (container.getStatus() != ContainerStatus.LOADED &&
                        container.getStatus() != ContainerStatus.RUNNING) {

                    System.out.println("🔄 [重载] 模型 " + runtimeId + " 正在恢复...");
                    long needed = container.getMemoryUsage();

                    synchronized (memoryMonitor) {
                        if (currentUsedMemoryMB + needed > config.getMaxMemoryMb()) {
                            tryEvictToMakeSpace(needed);
                        }
                        while (currentUsedMemoryMB + needed > config.getMaxMemoryMb()) {
                            long wait = timeoutMillis - (System.currentTimeMillis() - startTime);
                            if (wait <= 0) throw new RuntimeException("内存排队超时");
                            memoryMonitor.wait(wait);
                        }
                        if (container.load()) {
                            currentUsedMemoryMB += needed;
                        } else {
                            throw new RuntimeException("模型重载失败");
                        }
                    }
                }
            }

            // 3. 运行
            System.out.println("🚀 [执行] 模型 " + runtimeId + " Running...");
            return container.run(inputGroups);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("被中断", e);
        } catch (Exception e) {
            System.err.println("❌ 执行异常: " + e.getMessage());
            throw e;
        } finally {
            // 4. 释放
            synchronized (cpuMonitor) {
                currentRunningCount--;
                cpuMonitor.notifyAll();
            }
            synchronized (memoryMonitor) {
                memoryMonitor.notifyAll();
            }
        }
    }

    // ================== 3. 辅助方法 ==================

    private void tryEvictToMakeSpace(long targetNeeded) {
        long freedSoFar = 0;
        long maxMem = config.getMaxMemoryMb();
        long requiredToFree = (currentUsedMemoryMB + targetNeeded) - maxMem;

        if (requiredToFree <= 0) return;

        List<Integer> toEvict = new ArrayList<>();
        Iterator<Integer> it = lruQueue.iterator();

        while (it.hasNext() && freedSoFar < requiredToFree) {
            Integer candidateId = it.next();
            ModelContainer candidate = pool.get(candidateId);
            if (candidate == null) continue;

            if (candidate.getStatus() != ContainerStatus.RUNNING) {
                freedSoFar += candidate.getMemoryUsage();
                toEvict.add(candidateId);
            }
        }

        for (Integer id : toEvict) {
            ModelContainer c = pool.get(id);
            if (c != null) {
                c.unload();
                lruQueue.remove(id);
                currentUsedMemoryMB -= c.getMemoryUsage();
                System.out.println("♻️ [LRU淘汰] ID: " + id);
            }
        }
    }

    private void updateLruPosition(int runtimeId) {
        lruQueue.remove(runtimeId);
        lruQueue.offerLast(runtimeId);
    }

    public int size() { return pool.size(); }
    public long getCurrentUsedMemoryMB() { return currentUsedMemoryMB; }
    public ModelContainer getContainer(int runtimeId) { return pool.get(runtimeId); }
    public Map<Integer, ModelContainer> getAllContainers() { return Collections.unmodifiableMap(pool); }
}