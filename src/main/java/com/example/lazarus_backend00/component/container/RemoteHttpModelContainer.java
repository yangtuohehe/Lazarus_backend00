package com.example.lazarus_backend00.component.container;

import com.example.lazarus_backend00.domain.axis.Feature;
import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 远程服务模型容器 (Proxy Container) - 真实可用版
 * 职责：作为 HTTP 客户端，将分组的 TSDataBlock 序列化发送给远程推理服务。
 * 适配：单特征 TSDataBlock + List<List> 接口。
 */
public class RemoteHttpModelContainer implements ModelContainer {

    // ================== 元数据 ==================
    private final int containerId;
    private final Integer version;
    private final List<Parameter> parameterList;

    // 远程服务的 API 地址 (e.g., "http://192.168.1.100:8000/predict")
    private final String serviceUrl;

    // HTTP 客户端 & JSON 处理器
    private final HttpClient httpClient;
    private final ObjectMapper jsonMapper;

    // 运行时状态
    private final AtomicReference<ContainerStatus> status;
    private final AtomicInteger runtimeMemoryUsage;

    public RemoteHttpModelContainer(int containerId,
                                    Integer version,
                                    String serviceUrl,
                                    List<Parameter> parameterList) {
        this.containerId = containerId;
        this.version = version;
        this.serviceUrl = serviceUrl;
        this.parameterList = parameterList;

        this.status = new AtomicReference<>(ContainerStatus.CREATED);
        this.runtimeMemoryUsage = new AtomicInteger(5); // 仅占用少量网络 Buffer 内存

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.jsonMapper = new ObjectMapper();
    }

    // ================== 生命周期管理 (保持不变) ==================

    @Override
    public boolean load() {
        if (status.get() == ContainerStatus.LOADED) return true;

        System.out.println(">>> [Remote-HTTP] 正在连接远程服务: " + serviceUrl);

        try {
            // 约定：服务地址的 /health 路径用于健康检查
            // 如果 serviceUrl 是 http://host/predict，则 checkUrl 是 http://host/health
            String healthUrl = serviceUrl.contains("/predict")
                    ? serviceUrl.replace("/predict", "/health")
                    : serviceUrl + "/health";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                this.status.set(ContainerStatus.LOADED);
                return true;
            } else {
                System.err.println("远程服务健康检查失败: HTTP " + response.statusCode());
                this.status.set(ContainerStatus.FAILED);
                return false;
            }
        } catch (Exception e) {
            // 容错：有些服务可能没有 /health，尝试一次直接连接或视为失败，这里视为失败
            System.err.println("无法连接远程服务: " + e.getMessage());
            this.status.set(ContainerStatus.FAILED);
            return false;
        }
    }

    @Override
    public boolean unload() {
        this.status.set(ContainerStatus.UNLOADED);
        System.out.println("<<< [Remote-HTTP] 断开连接 [" + containerId + "]");
        return true;
    }

    // ================== 核心推理 (二维列表适配) ==================

    @Override
    public List<List<TSDataBlock>> run(List<List<TSDataBlock>> inputGroups) {
        if (status.get() != ContainerStatus.LOADED) {
            throw new IllegalStateException("远程容器未就绪。");
        }

        try {
            // 1. 序列化：Java Objects -> JSON DTO
            RemoteInferenceRequest requestDto = convertToDto(inputGroups);
            String requestBody = jsonMapper.writeValueAsString(requestDto);

            // 2. 发送请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(60)) // 推理通常较慢，设置 60s 超时
                    .build();

            // 3. 接收响应
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("远程推理失败: HTTP " + response.statusCode() + " | Body: " + response.body());
            }

            // 4. 反序列化：JSON -> Response DTO
            RemoteInferenceResponse responseDto = jsonMapper.readValue(response.body(), RemoteInferenceResponse.class);

            // 5. 结果还原：DTO -> Java Objects
            // 需要输入数据的第一个块作为时空元数据模板
            TSDataBlock template = inputGroups.get(0).get(0);
            return convertFromDto(responseDto, template);

        } catch (Exception e) {
            this.status.set(ContainerStatus.FAILED);
            throw new RuntimeException("远程调用异常: " + e.getMessage(), e);
        } finally {
            // 恢复状态 (如果是临时网络抖动，不应永久置为 FAILED，但在 load() 逻辑中通常需要手动恢复)
            // 这里简单处理：如果只是本次失败，尝试恢复 LOADED
            if (status.get() == ContainerStatus.FAILED) {
                // 生产环境可能需要更复杂的熔断机制
                this.status.set(ContainerStatus.LOADED);
            }
        }
    }

    // ================== DTO 定义 (内部类) ==================

    // 请求体结构：{ "inputs": [ [Group0_Block0, Group0_Block1], [Group1_Block0] ] }
    private static class RemoteInferenceRequest {
        public List<List<RemoteBlockDto>> inputs;
    }

    // 响应体结构：{ "outputs": [ [Group0_Block0...], ... ] }
    private static class RemoteInferenceResponse {
        public List<List<RemoteBlockDto>> outputs;
    }

    // 单个 Block 的传输对象
    private static class RemoteBlockDto {
        public int featureId;
        public long[] shape;
        public float[] data;
    }

    // ================== 转换逻辑 ==================

    /**
     * Java -> DTO
     */
    private RemoteInferenceRequest convertToDto(List<List<TSDataBlock>> inputGroups) {
        RemoteInferenceRequest req = new RemoteInferenceRequest();
        req.inputs = new ArrayList<>();

        for (List<TSDataBlock> group : inputGroups) {
            List<RemoteBlockDto> groupDto = new ArrayList<>();
            for (TSDataBlock block : group) {
                RemoteBlockDto dto = new RemoteBlockDto();
                dto.featureId = block.getFeatureId();
                dto.shape = block.getDynamicShape();
                dto.data = block.getData(); // 直接获取 float[]
                groupDto.add(dto);
            }
            req.inputs.add(groupDto);
        }
        return req;
    }

    /**
     * DTO -> Java
     */
    private List<List<TSDataBlock>> convertFromDto(RemoteInferenceResponse res, TSDataBlock template) {
        List<List<TSDataBlock>> allOutputs = new ArrayList<>();

        if (res.outputs == null) return allOutputs;

        // 获取预期的输出参数定义 (按 Order 排序)
        List<Parameter> outputParams = getSortedOutputParameters();

        // 遍历 DTO 中的输出组 (对应 Parameter 的 Tensor)
        for (int i = 0; i < res.outputs.size(); i++) {
            if (i >= outputParams.size()) break; // 防止越界

            List<RemoteBlockDto> groupDto = res.outputs.get(i);
            Parameter param = outputParams.get(i);
            List<Feature> features = param.getFeatureList(); // 预期的特征列表

            List<TSDataBlock> groupBlocks = new ArrayList<>();

            // 遍历组内的特征块
            for (int j = 0; j < groupDto.size(); j++) {
                RemoteBlockDto dto = groupDto.get(j);

                // 确定 FeatureID：优先信赖远程返回的 ID，如果为 0 或空，则使用本地 Parameter 定义的 ID
                int finalFeatureId = dto.featureId;
                if (finalFeatureId == 0 && j < features.size()) {
                    finalFeatureId = features.get(j).getId();
                }

                // 构建 Block
                TSDataBlock.Builder builder = new TSDataBlock.Builder();
                builder.featureId(finalFeatureId);
                builder.data(dto.data);

                // 复制时空元数据
                copyMetadata(template, builder);

                groupBlocks.add(builder.build());
            }
            allOutputs.add(groupBlocks);
        }
        return allOutputs;
    }

    // ================== 辅助方法 ==================

    private List<Parameter> getSortedOutputParameters() {
        return parameterList.stream()
                .filter(p -> "OUTPUT".equalsIgnoreCase(p.getIoType()))
                .sorted(Comparator.comparingInt(Parameter::getTensorOrder))
                .collect(Collectors.toList());
    }

    private void copyMetadata(TSDataBlock template, TSDataBlock.Builder builder) {
        if (template.getTAxis() != null) {
            if (template.getBatchSize() > 1) {
                builder.batchTime(template.getBatchTOrigins(), template.getTAxis());
            } else {
                builder.time(template.getTOrigin(), template.getTAxis());
            }
        }
        if (template.getZAxis() != null) builder.z(template.getZOrigin(), template.getZAxis());
        if (template.getYAxis() != null) builder.y(template.getYOrigin(), template.getYAxis());
        if (template.getXAxis() != null) builder.x(template.getXOrigin(), template.getXAxis());
    }

    // ================== Getters ==================
    @Override public int getContainerId() { return containerId; }
    @Override public Integer getVersion() { return version; }
    @Override public List<Parameter> getParameterList() { return parameterList; }
    @Override public ContainerStatus getStatus() { return status.get(); }
    @Override public int getMemoryUsage() { return runtimeMemoryUsage.get(); }
}