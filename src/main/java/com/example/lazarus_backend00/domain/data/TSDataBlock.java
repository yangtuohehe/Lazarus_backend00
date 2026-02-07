package com.example.lazarus_backend00.domain.data;

import com.example.lazarus_backend00.domain.axis.SpaceAxisX;
import com.example.lazarus_backend00.domain.axis.SpaceAxisY;
import com.example.lazarus_backend00.domain.axis.SpaceAxisZ;
import com.example.lazarus_backend00.domain.axis.TimeAxis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TSDataBlock (单特征修正版)
 * 特性：
 * 1. 仅存储单个特征的数据 (featureId + float[])。
 * 2. 包含 getDynamicShape 以支持模型容器。
 * 3. 包含 getLastTimestamp 以支持编排服务。
 */
public class TSDataBlock {

    // ================== 核心数据 (单特征) ==================
    private final int featureId;
    private final float[] data; // 扁平化的一维数组

    // ================== Batch 与 维度定义 ==================
    private final int batchSize;
    private final List<Instant> batchTOrigins;

    private final TimeAxis tAxis;
    private final double zOrigin; private final SpaceAxisZ zAxis;
    private final double yOrigin; private final SpaceAxisY yAxis;
    private final double xOrigin; private final SpaceAxisX xAxis;

    private TSDataBlock(Builder builder) {
        this.featureId = builder.featureId;
        this.data = builder.data;
        this.batchSize = builder.batchSize;
        this.batchTOrigins = builder.batchTOrigins;

        this.tAxis = builder.tAxis;
        this.zOrigin = builder.zOrigin;
        this.zAxis = builder.zAxis;
        this.yOrigin = builder.yOrigin;
        this.yAxis = builder.yAxis;
        this.xOrigin = builder.xOrigin;
        this.xAxis = builder.xAxis;
    }

    // ================== 维度与形状推演 (Container 必需) ==================

    /**
     * 获取单个样本的网格大小 (不含 Batch)
     */
    public int getSingleGridSize() {
        int t = (tAxis != null) ? tAxis.getCount() : 1;
        int z = (zAxis != null) ? zAxis.getCount() : 1;
        int y = (yAxis != null) ? yAxis.getCount() : 1;
        int x = (xAxis != null) ? xAxis.getCount() : 1;
        return t * z * y * x;
    }

    /**
     * 获取动态形状 [N, T, Z, Y, X]
     */
    public long[] getDynamicShape() {
        List<Long> shapeList = new ArrayList<>();
        // 0. Batch
        shapeList.add((long) batchSize);
        // 1. Time
        if (tAxis != null) shapeList.add((long) tAxis.getCount());
        // 2. Space (Z, Y, X)
        if (zAxis != null) shapeList.add((long) zAxis.getCount());
        if (yAxis != null) shapeList.add((long) yAxis.getCount());
        if (xAxis != null) shapeList.add((long) xAxis.getCount());

        return shapeList.stream().mapToLong(Long::longValue).toArray();
    }

    // ================== 辅助计算 (Orchestrator 必需) ==================

    public Instant getLastTimestamp() {
        if (tAxis == null) return getTOrigin();

        Instant lastStart = (batchTOrigins != null && !batchTOrigins.isEmpty())
                ? batchTOrigins.get(batchTOrigins.size() - 1)
                : getTOrigin();

        // 计算逻辑：range = resolution * count
        // 注意：resolution是Double，count是Integer，转long可能有精度损耗，视具体需求
        long duration = Math.round(tAxis.getResolution() * tAxis.getCount());
        return lastStart.plusSeconds(duration);
    }

    // ================== Builder (单特征适配) ==================
    public static class Builder {
        private int featureId;
        private float[] data;

        private int batchSize = 1;
        private List<Instant> batchTOrigins = new ArrayList<>();

        private TimeAxis tAxis;
        private double zOrigin = 0.0; private SpaceAxisZ zAxis;
        private double yOrigin = 0.0; private SpaceAxisY yAxis;
        private double xOrigin = 0.0; private SpaceAxisX xAxis;

        public Builder() {}

        // 🔥 必须显式设置 FeatureID
        public Builder featureId(int featureId) {
            this.featureId = featureId;
            return this;
        }

        // 🔥 必须显式设置数据数组
        public Builder data(float[] data) {
            this.data = data;
            return this;
        }

        public Builder time(Instant origin, TimeAxis axis) {
            this.batchSize = 1;
            this.batchTOrigins = new ArrayList<>();
            if (origin != null) this.batchTOrigins.add(origin);
            this.tAxis = axis;
            return this;
        }

        public Builder batchTime(List<Instant> origins, TimeAxis axis) {
            this.batchSize = origins != null ? origins.size() : 0;
            this.batchTOrigins = origins != null ? new ArrayList<>(origins) : new ArrayList<>();
            this.tAxis = axis;
            return this;
        }

        public Builder z(double origin, SpaceAxisZ axis) { this.zOrigin = origin; this.zAxis = axis; return this; }
        public Builder y(double origin, SpaceAxisY axis) { this.yOrigin = origin; this.yAxis = axis; return this; }
        public Builder x(double origin, SpaceAxisX axis) { this.xOrigin = origin; this.xAxis = axis; return this; }

        public TSDataBlock build() {
            if (data == null) {
                throw new IllegalArgumentException("TSDataBlock 构建失败: 数据不能为空");
            }
            return new TSDataBlock(this);
        }
    }

    // ================== Getters ==================
    public int getFeatureId() { return featureId; }
    public float[] getData() { return data; }

    public int getBatchSize() { return batchSize; }
    public List<Instant> getBatchTOrigins() { return Collections.unmodifiableList(batchTOrigins); }
    public Instant getTOrigin() { return batchTOrigins.isEmpty() ? null : batchTOrigins.get(0); }
    public TimeAxis getTAxis() { return tAxis; }
    public double getZOrigin() { return zOrigin; }
    public SpaceAxisZ getZAxis() { return zAxis; }
    public double getYOrigin() { return yOrigin; }
    public SpaceAxisY getYAxis() { return yAxis; }
    public double getXOrigin() { return xOrigin; }
    public SpaceAxisX getXAxis() { return xAxis; }
}