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
 * TSDataBlock (完美继承版)
 * 继承自 TSShell，仅保留数据和 Batch 相关的特有属性，空间维度全权交由父类管理。
 */
public class TSDataBlock extends TSShell {

    // ================== 核心数据 (子类特有) ==================
    private final float[] data;
    private final int batchSize;
    private final List<Instant> batchTOrigins;

    /**
     * 核心构造：接收一个父类外壳，加上自己的特有数据
     */
    protected TSDataBlock(TSShell baseShell, float[] data, int batchSize, List<Instant> batchTOrigins) {
        super(baseShell); // 完美调用父类的拷贝构造函数，继承所有时空坐标
        this.data = data;
        this.batchSize = batchSize;
        this.batchTOrigins = batchTOrigins != null ? batchTOrigins : new ArrayList<>();
    }

    // ================== 维度与形状推演 (调用父类的 Getter) ==================
    public int getSingleGridSize() {
        int t = (getTAxis() != null) ? getTAxis().getCount() : 1;
        int z = (getZAxis() != null) ? getZAxis().getCount() : 1;
        int y = (getYAxis() != null) ? getYAxis().getCount() : 1;
        int x = (getXAxis() != null) ? getXAxis().getCount() : 1;
        return t * z * y * x;
    }

    public long[] getDynamicShape() {
        List<Long> shapeList = new ArrayList<>();
        shapeList.add((long) batchSize);
        if (getTAxis() != null) shapeList.add((long) getTAxis().getCount());
        if (getZAxis() != null) shapeList.add((long) getZAxis().getCount());
        if (getYAxis() != null) shapeList.add((long) getYAxis().getCount());
        if (getXAxis() != null) shapeList.add((long) getXAxis().getCount());
        return shapeList.stream().mapToLong(Long::longValue).toArray();
    }

    public Instant getLastTimestamp() {
        if (getTAxis() == null) return getTOrigin();
        Instant lastStart = (batchTOrigins != null && !batchTOrigins.isEmpty())
                ? batchTOrigins.get(batchTOrigins.size() - 1)
                : getTOrigin();
        long duration = Math.round(getTAxis().getResolution() * getTAxis().getCount());
        return lastStart.plusSeconds(duration);
    }

    // ================== 子类特有 Getters ==================
    public float[] getData() { return data; }
    public int getBatchSize() { return batchSize; }
    public List<Instant> getBatchTOrigins() { return Collections.unmodifiableList(batchTOrigins); }

    // ================== Builder (完全向后兼容容器层) ==================
    public static class Builder {
        private int featureId;
        private float[] data;
        private int batchSize = 1;
        private List<Instant> batchTOrigins = new ArrayList<>();

        // 临时变量，用于最后组装父类 TSShell
        private Instant tOrigin; private TimeAxis tAxis;
        private double zOrigin = 0.0; private SpaceAxisZ zAxis;
        private double yOrigin = 0.0; private SpaceAxisY yAxis;
        private double xOrigin = 0.0; private SpaceAxisX xAxis;

        public Builder() {}

        public Builder featureId(int featureId) { this.featureId = featureId; return this; }
        public Builder data(float[] data) { this.data = data; return this; }

        public Builder time(Instant origin, TimeAxis axis) {
            this.batchSize = 1;
            this.batchTOrigins = new ArrayList<>();
            if (origin != null) this.batchTOrigins.add(origin);
            this.tOrigin = origin;
            this.tAxis = axis;
            return this;
        }

        public Builder batchTime(List<Instant> origins, TimeAxis axis) {
            this.batchSize = origins != null ? origins.size() : 0;
            this.batchTOrigins = origins != null ? new ArrayList<>(origins) : new ArrayList<>();
            this.tOrigin = this.batchTOrigins.isEmpty() ? null : this.batchTOrigins.get(0);
            this.tAxis = axis;
            return this;
        }

        public Builder z(double origin, SpaceAxisZ axis) { this.zOrigin = origin; this.zAxis = axis; return this; }
        public Builder y(double origin, SpaceAxisY axis) { this.yOrigin = origin; this.yAxis = axis; return this; }
        public Builder x(double origin, SpaceAxisX axis) { this.xOrigin = origin; this.xAxis = axis; return this; }

        public TSDataBlock build() {
            if (data == null) throw new IllegalArgumentException("TSDataBlock 构建失败: 数据不能为空");

            // 1. 先组装父类外壳
            TSShell baseShell = new TSShell.Builder(featureId)
                    .time(tOrigin, tAxis)
                    .z(zOrigin, zAxis)
                    .y(yOrigin, yAxis)
                    .x(xOrigin, xAxis)
                    .build();

            // 2. 将父类外壳与子类数据结合
            return new TSDataBlock(baseShell, data, batchSize, batchTOrigins);
        }
    }
}