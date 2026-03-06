package com.example.lazarus_backend00.subtest;

import com.example.lazarus_backend00.LazarusBackend00Application;
import com.example.lazarus_backend00.component.clock.VirtualTimeTickEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Scanner;

/**
 * 数据子系统仿真步进器 (普通程序版)
 * 解决 JUnit 控制台只读问题，实现完全交互。
 */
@Component
public class DataSubsystemSimulator {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // 🔥 读取配置文件中的初始时间
    @Value("${simulation.start-time:1970-01-01T00:00:00Z}")
    private String startTimeStr;

    /**
     * 程序入口
     */
    public static void main(String[] args) {
        // 1. 启动 Spring 容器 (指向你的主启动类)
        ConfigurableApplicationContext ctx = SpringApplication.run(LazarusBackend00Application.class, args);

        // 2. 从容器中获取当前的模拟器 Bean
        DataSubsystemSimulator simulator = ctx.getBean(DataSubsystemSimulator.class);

        // 3. 运行业务逻辑
        simulator.runInteractiveLoop();

        // 4. 退出
        ctx.close();
    }

    public void runInteractiveLoop() {
        // 解析初始时刻
        Instant currentVirtualTime = Instant.parse(startTimeStr);

        System.out.println("\n\n" + "=".repeat(50));
        System.out.println("🎛️  数据子系统【手动增量步进】控制台");
        System.out.println("📅  系统设定初始起点: [" + startTimeStr + "]");
        System.out.println("👉  输入数字: 前进指定小时数 (如 1, 8, 24)");
        System.out.println("👉  直接回车: 默认前进 1 小时");
        System.out.println("👉  输入 'exit': 退出模拟");
        System.out.println("=".repeat(50) + "\n");

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("📍  当前仿真时刻: \033[32m" + currentVirtualTime + "\033[0m"); // 绿色高亮显示时间
            System.out.print("⌨️  请输入步进小时数 > ");

            // 这里现在绝对可以输入了！
            String input = scanner.nextLine().trim();

            if ("exit".equalsIgnoreCase(input)) {
                break;
            }

            int hoursToStep = 1;
            if (!input.isEmpty()) {
                try {
                    hoursToStep = Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    System.err.println("❌  错误：请输入有效的整数数字！");
                    continue;
                }
            }

            // 执行步进循环
            System.out.println("🚀  正在加速推进 " + hoursToStep + " 小时...");
            for (int i = 0; i < hoursToStep; i++) {
                currentVirtualTime = currentVirtualTime.plus(1, ChronoUnit.HOURS);

                // 核心动作：向系统发送时钟跳动事件
                eventPublisher.publishEvent(new VirtualTimeTickEvent(this, currentVirtualTime));
            }

            System.out.println("✅  步进完成。当前最新状态已同步。");
            System.out.println("-".repeat(50));
        }

        System.out.println("🏁  模拟器已安全关闭。");
        scanner.close();
    }
}