package com.example.lazarus_backend00.subtest;

import com.example.lazarus_backend00.component.clock.VirtualTimeTickEvent;
import com.example.lazarus_backend00.service.subservice.DataSubsystemServiceImpl;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Scanner;

public class DataSubsystemSimulator {

    private static final DateTimeFormatter FMT_LOG = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public static void main(String[] args) {
        // 1. 初始化纯粹的数据子系统
        DataSubsystemServiceImpl subsystemService = new DataSubsystemServiceImpl();

        // 2. 初始时间
        Instant sysTime = Instant.parse("2022-01-01T00:00:00Z");
        Scanner scanner = new Scanner(System.in);

        System.out.println("====== 数据子系统独立测试台 (Data Subsystem Simulator) ======");
        System.out.println("初始上帝时间: " + FMT_LOG.format(sysTime));
        System.out.println("----------------------------------------------");

        while (true) {
            System.out.print("\n👉 请输入要步进的【小时数】(如 12)，或 'Q' 退出: ");
            String input = scanner.next();

            if ("Q".equalsIgnoreCase(input)) break;

            try {
                int hours = Integer.parseInt(input);
                sysTime = sysTime.plus(hours, ChronoUnit.HOURS);
                System.out.println("=========================================================");
                System.out.println("🕒 上帝时间步进 -> " + FMT_LOG.format(sysTime));

                // 3. 核心：直接发送时钟跳动事件给子系统！
                VirtualTimeTickEvent event = new VirtualTimeTickEvent("Simulator", sysTime);
                subsystemService.onVirtualTimeTick(event);

            } catch (Exception e) {
                System.out.println("无效输入，请输入数字。");
            }
        }
        scanner.close();
    }
}