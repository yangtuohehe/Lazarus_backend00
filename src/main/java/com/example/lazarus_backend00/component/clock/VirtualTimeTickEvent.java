package com.example.lazarus_backend00.component.clock;

import org.springframework.context.ApplicationEvent;
import java.time.Instant;

/**
 * 虚拟时间跳动事件
 * 当上帝时钟拨动时，广播此事件。
 */
public class VirtualTimeTickEvent extends ApplicationEvent {

    private final Instant virtualTime; // 当前的虚拟时刻

    public VirtualTimeTickEvent(Object source, Instant virtualTime) {
        super(source);
        this.virtualTime = virtualTime;
    }

    public Instant getVirtualTime() {
        return virtualTime;
    }
}
