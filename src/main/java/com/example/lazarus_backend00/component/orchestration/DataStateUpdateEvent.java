package com.example.lazarus_backend00.component.orchestration;

import com.example.lazarus_backend00.domain.data.TSState;
import org.springframework.context.ApplicationEvent;
import java.util.List;

/**
 * 数据状态聚合更新广播事件
 */
public class DataStateUpdateEvent extends ApplicationEvent {
    private final List<TSState> tsStates;
    private final boolean isReplacedCorrection; // 是否由替换态(纠偏)触发

    public DataStateUpdateEvent(Object source, List<TSState> tsStates, boolean isReplacedCorrection) {
        super(source);
        this.tsStates = tsStates;
        this.isReplacedCorrection = isReplacedCorrection;
    }

    public List<TSState> getTsStates() {
        return tsStates;
    }

    public boolean isReplacedCorrection() {
        return isReplacedCorrection;
    }
}