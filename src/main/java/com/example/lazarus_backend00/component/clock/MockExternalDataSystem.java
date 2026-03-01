//package com.example.lazarus_backend00.component.clock;
//
//import com.example.lazarus_backend00.domain.data.TSDataBlock;
//import com.example.lazarus_backend00.service.DataService;
//import org.springframework.context.event.EventListener;
//import org.springframework.stereotype.Component;
//
//import java.time.Duration;
//import java.time.Instant;
//
///**
// * 模拟外部数据源系统
// * 职责：监听虚拟时间，模拟真实世界的“数据传输延迟”。
// */
//@Component
//public class MockExternalDataSystem {
//
//    private final DataService dataService; // 连接到你的主系统接口
//
//    // 模拟传输延迟：10分钟
//    // 意味着：当虚拟世界是 12:00 时，系统只能收到 11:50 的数据
//    private final Duration transmissionLatency = Duration.ofMinutes(10);
//
//    public MockExternalDataSystem(DataService dataService) {
//        this.dataService = dataService;
//    }
//
//    /**
//     * 👂 监听上帝时钟
//     */
//    @EventListener
//    public void onVirtualTimeTick(VirtualTimeTickEvent event) {
//        Instant virtualNow = event.getVirtualTime();
//
//        // 1. 计算这一刻“实际上”能收到的数据时间
//        Instant availableDataTime = virtualNow.minus(transmissionLatency);
//
//        System.out.println("📡 [MockDataSystem] 收到授时: " + virtualNow
//                + "。模拟延迟后，发送时刻 " + availableDataTime + " 的数据。");
//
//        // 2. 生成/读取该时刻的数据 (这里用 Mock 数据代替)
//        // 实际上这里你会去读取 NC 文件或者数据库中对应 availableDataTime 的记录
//        TSDataBlock delayedData = generateMockData(availableDataTime);
//
//        // 3. 推送给主系统的 DataService
//        // 注意：这模拟的是数据刚刚通过网络传过来
//        dataService.pushData(101, delayedData);
//    }
//
//    private TSDataBlock generateMockData(Instant time) {
//        // ... 生成对应 time 的数据块 ...
//        return null; // 略
//    }
//}