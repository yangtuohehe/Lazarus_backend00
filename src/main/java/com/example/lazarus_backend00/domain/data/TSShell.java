package com.example.lazarus_backend00.domain.data;

import com.example.lazarus_backend00.domain.axis.SpaceAxisX;
import com.example.lazarus_backend00.domain.axis.SpaceAxisY;
import com.example.lazarus_backend00.domain.axis.SpaceAxisZ;
import com.example.lazarus_backend00.domain.axis.TimeAxis;

import java.time.Instant;

/**
 * Time-Space Shell (时空外壳)
 * 作用：仅包含时空网格的元数据和边界，不包含任何真实数据。
 * 用途：用于触发器比对、数据库空间索引 (GIST) 和前端目录请求。
 * 特性：支持动态维度坍缩。无论是否存在高空 Z 轴，系统都能自动分配正确的 [Time, Feature, (Z), Y, X] 紧凑张量索引。
 */
public class TSShell {

    // [Dimension 1: Feature] 物理特征 (如: 101=温度, 102=降水)
    private final int featureId;

    // ================== [T] 时间维度 ==================
    private Instant tOrigin;            // 时间起点
    private Instant tEnd;               // 时间终点
    private final Double tResolution;   // 时间分辨率
    private final TimeAxis tAxis;       // 时间轴对象

    // ================== [Z] 垂直维度 ==================
    private final double zOrigin;       // 高度起点 (下界)
    private final double zEnd;          // 高度终点 (上界)
    private final Double zResolution;   // 高度分辨率
    private final SpaceAxisZ zAxis;     // Z轴对象

    // ================== [Y] 纬度维度 ==================
    private final double yOrigin;       // 纬度起点 (南界)
    private final double yEnd;          // 纬度终点 (北界)
    private final Double yResolution;   // 纬向分辨率
    private final SpaceAxisY yAxis;     // Y轴对象

    // ================== [X] 经度维度 ==================
    private final double xOrigin;       // 经度起点 (西界)
    private final double xEnd;          // 经度终点 (东界)
    private final Double xResolution;   // 经向分辨率
    private final SpaceAxisX xAxis;     // X轴对象

    private TSShell(Builder builder) {
        this.featureId = builder.featureId;

        this.tOrigin = builder.tOrigin;
        this.tEnd = builder.tEnd;
        this.tResolution = builder.tResolution;
        this.tAxis = builder.tAxis;

        this.zOrigin = builder.zOrigin;
        this.zEnd = builder.zEnd;
        this.zResolution = builder.zResolution;
        this.zAxis = builder.zAxis;

        this.yOrigin = builder.yOrigin;
        this.yEnd = builder.yEnd;
        this.yResolution = builder.yResolution;
        this.yAxis = builder.yAxis;

        this.xOrigin = builder.xOrigin;
        this.xEnd = builder.xEnd;
        this.xResolution = builder.xResolution;
        this.xAxis = builder.xAxis;
    }

    /**
     * 仿真系统时钟推演：更新时间锚点，用于触发器生成未来的任务。
     * 优势：对象复用，不触发 GC 回收。
     */
    public void upDateNew() {
        if (tOrigin != null && tEnd != null && tResolution != null) {
            long stepSeconds = tResolution.longValue();
            this.tOrigin = this.tOrigin.plusSeconds(stepSeconds);
            this.tEnd = this.tEnd.plusSeconds(stepSeconds);
        }
    }

    // ================== 维度探针 (极度实用) ==================

    /** 判断是否存在时间维度 */
    public boolean hasTime() { return tAxis != null; }
    /** 判断是否存在 Z 轴 */
    public boolean hasZ() { return zAxis != null; }
    /** 判断是否存在空间面 (Y 和 X) */
    public boolean hasSpace() { return yAxis != null && xAxis != null; }


    // ================== 内部建造者类 (The Builder) ==================
    public static class Builder {
        private final int featureId;

        private Instant tOrigin;
        private Instant tEnd;
        private Double tResolution;
        private TimeAxis tAxis;

        private double zOrigin = 0.0;
        private double zEnd = 0.0;
        private Double zResolution;
        private SpaceAxisZ zAxis;

        private double yOrigin = 0.0;
        private double yEnd = 0.0;
        private Double yResolution;
        private SpaceAxisY yAxis;

        private double xOrigin = 0.0;
        private double xEnd = 0.0;
        private Double xResolution;
        private SpaceAxisX xAxis;

        public Builder(int featureId) {
            this.featureId = featureId;
        }

        public Builder time(Instant origin, TimeAxis axis) {
            this.tOrigin = origin;
            this.tAxis = axis;
            if (axis != null) {
                this.tResolution = axis.getResolution();
                this.tEnd = origin.plusSeconds((long)(axis.getResolution() * axis.getCount()));
            }
            return this;
        }

        public Builder z(double origin, SpaceAxisZ axis) {
            this.zOrigin = origin;
            this.zAxis = axis;
            if (axis != null) {
                this.zResolution = axis.getResolution();
                this.zEnd = origin + (axis.getResolution() * axis.getCount());
            }
            return this;
        }

        public Builder y(double origin, SpaceAxisY axis) {
            this.yOrigin = origin;
            this.yAxis = axis;
            if (axis != null) {
                this.yResolution = axis.getResolution();
                this.yEnd = origin + (axis.getResolution() * axis.getCount());
            }
            return this;
        }

        public Builder x(double origin, SpaceAxisX axis) {
            this.xOrigin = origin;
            this.xAxis = axis;
            if (axis != null) {
                this.xResolution = axis.getResolution();
                this.xEnd = origin + (axis.getResolution() * axis.getCount());
            }
            return this;
        }

        /**
         * 🌟 核心升级：动态维度坍缩算法
         * 根据当前存活的轴，动态分配张量维度索引 (Dimension Index)，消除空洞。
         */
        public TSShell build() {
            int currentDimIndex = 0; // 维度发牌官，从 0 开始发牌

            // 1. 发牌给 T (Time)
            if (this.tAxis != null) {
                this.tAxis.setDimensionIndex(currentDimIndex++); // 拿到 0，计数器变 1
            }

            // 2. 预留给 F (Feature)
            // 特征通道在张量中总是占据一维，所以必须强行让计数器 +1
            currentDimIndex++; // 此时计数器变 2

            // 3. 发牌给 Z (Height)
            if (this.zAxis != null) {
                this.zAxis.setDimensionIndex(currentDimIndex++); // 如果有Z，拿到 2；计数器变 3
            }

            // 4. 发牌给 Y (Latitude)
            if (this.yAxis != null) {
                this.yAxis.setDimensionIndex(currentDimIndex++); // 如果无Z，Y拿到 2；有Z，Y拿到 3
            }

            // 5. 发牌给 X (Longitude)
            if (this.xAxis != null) {
                this.xAxis.setDimensionIndex(currentDimIndex++); // X 自动变成最后一位
            }

            return new TSShell(this);
        }
    }

    // ================== Getters (MyBatis/JPA 查询全依赖它们) ==================
    public int getFeatureId() { return featureId; }

    public Instant getTOrigin() { return tOrigin; }
    public Instant getTEnd() { return tEnd; }
    public Double getTResolution() { return tResolution; }
    public TimeAxis getTAxis() { return tAxis; }

    public double getZOrigin() { return zOrigin; }
    public double getZEnd() { return zEnd; }
    public Double getZResolution() { return zResolution; }
    public SpaceAxisZ getZAxis() { return zAxis; }

    public double getYOrigin() { return yOrigin; }
    public double getYEnd() { return yEnd; }
    public Double getYResolution() { return yResolution; }
    public SpaceAxisY getYAxis() { return yAxis; }

    public double getXOrigin() { return xOrigin; }
    public double getXEnd() { return xEnd; }
    public Double getXResolution() { return xResolution; }
    public SpaceAxisX getXAxis() { return xAxis; }
}