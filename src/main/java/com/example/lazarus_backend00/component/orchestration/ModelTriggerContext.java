package com.example.lazarus_backend00.component.orchestration;

import com.example.lazarus_backend00.component.container.Parameter;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 结构化的模型触发契约
 * 负责将 List<Parameter> 解析为输入/输出的张量顺序，供引擎执行时空拓扑校验
 */
public class ModelTriggerContext {

    private final int containerId;
    private final Duration step;
    private final Duration window;

    // 按 tensorOrder 升序排列的输入与输出参数图纸
    private final List<Parameter> inputParams;
    private final List<Parameter> outputParams;

    public ModelTriggerContext(int containerId, List<Parameter> params, Duration step, Duration window) {
        this.containerId = containerId;
        this.step = step;
        this.window = window;

        // 过滤并排序输入参数 (对应模型的 Input Tensors)
        this.inputParams = params.stream()
                .filter(p -> "INPUT".equalsIgnoreCase(p.getIoType()))
                .sorted((p1, p2) -> Integer.compare(p1.getTensorOrder(), p2.getTensorOrder()))
                .collect(Collectors.toList());

        // 过滤并排序输出参数 (对应模型的 Output Tensors)
        this.outputParams = params.stream()
                .filter(p -> "OUTPUT".equalsIgnoreCase(p.getIoType()))
                .sorted((p1, p2) -> Integer.compare(p1.getTensorOrder(), p2.getTensorOrder()))
                .collect(Collectors.toList());
    }

    public int getContainerId() { return containerId; }
    public Duration getStep() { return step; }
    public Duration getWindow() { return window; }
    public List<Parameter> getInputParams() { return inputParams; }
    public List<Parameter> getOutputParams() { return outputParams; }
}