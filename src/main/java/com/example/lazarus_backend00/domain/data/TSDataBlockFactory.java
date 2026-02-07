package com.example.lazarus_backend00.domain.data;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TSDataBlock 制造工厂 (单特征适配版)
 * 职责：将轻量级的元数据外壳 (TSShell) 与底层查询出的实体数据数组融合。
 * 修改说明：适配 TSDataBlock 的单特征结构，移除了 Map 逻辑。
 */
public class TSDataBlockFactory {

    /**
     * 【模式一：单样本构建】
     * 根据单个 TSShell 复制时空网格，Batch 维度自动设为 1。
     *
     * @param shell 单任务的时空外壳 (包含 FeatureID)
     * @param data  原始数据数组 (对应 shell.getFeatureId())
     */
    public static TSDataBlock createFromShell(TSShell shell, float[] data) {
        TSDataBlock.Builder builder = new TSDataBlock.Builder();

        // 1. 设置核心特征 ID
        builder.featureId(shell.getFeatureId());

        // 2. 设置时间 (调用单点时间接口，内部 batchSize=1)
        if (shell.hasTime()) {
            builder.time(shell.getTOrigin(), shell.getTAxis());
        }

        // 3. 设置空间与数据
        applySpatialAndData(builder, shell, data);

        return builder.build();
    }

    /**
     * 【模式二：批量构建 (Batch Inference 专用)】
     * 将多个时空外壳合并为一个 Batch Tensor。
     * 假设：所有 Shell 的 FeatureID、空间网格 (X/Y/Z) 和时间分辨率 (TAxis) 必须完全一致。
     *
     * @param batchShells 任务外壳列表 (长度 N)
     * @param mergedData  已合并的大数组 (长度 N * GridSize)
     */
    public static TSDataBlock createFromBatch(List<TSShell> batchShells, float[] mergedData) {
        if (batchShells == null || batchShells.isEmpty()) {
            throw new IllegalArgumentException("无法从空的外壳列表构建 DataBlock");
        }

        TSDataBlock.Builder builder = new TSDataBlock.Builder();

        // 取第一个 Shell 作为模板
        TSShell template = batchShells.get(0);

        // 1. 设置核心特征 ID (假设所有 Shell 的 FeatureID 一致)
        builder.featureId(template.getFeatureId());

        // 2. 提取 Batch 时间原点列表 (List<Instant>)
        if (template.hasTime()) {
            List<Instant> batchOrigins = batchShells.stream()
                    .map(TSShell::getTOrigin)
                    .collect(Collectors.toList());

            // 调用新的批量时间接口
            builder.batchTime(batchOrigins, template.getTAxis());
        }

        // 3. 设置空间与数据 (复用逻辑)
        applySpatialAndData(builder, template, mergedData);

        return builder.build();
    }

    // ================== 私有复用逻辑 ==================

    private static void applySpatialAndData(TSDataBlock.Builder builder,
                                            TSShell template,
                                            float[] data) {
        // 设置空间轴 (Z, Y, X)
        if (template.hasZ()) builder.z(template.getZOrigin(), template.getZAxis());
        if (template.hasSpace()) {
            builder.y(template.getYOrigin(), template.getYAxis());
            builder.x(template.getXOrigin(), template.getXAxis());
        }

        // 注入数据 (直接传入数组，不再遍历 Map)
        builder.data(data);
    }
}