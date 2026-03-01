package com.example.lazarus_backend00.domain.data;

import com.example.lazarus_backend00.domain.axis.*;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.Instant;

/**
 * Time-Space Shell (时空外壳) - JSON 反序列化终极修复版
 */
public class TSShell {

    @JsonProperty("featureId")
    private int featureId;

    // ================== [T] 时间维度 ==================
    private Instant tOrigin;
    private Instant tEnd;

    @JsonProperty("tResolution") @JsonAlias({"tresolution", "TResolution"}) private Double tResolution;
    @JsonProperty("tAxis") @JsonAlias({"taxis", "TAxis"}) private TimeAxis tAxis;

    // ================== [Z] 垂直维度 ==================
    private double zOrigin; private double zEnd;
    @JsonProperty("zResolution") @JsonAlias({"zresolution"}) private Double zResolution;
    @JsonProperty("zAxis") @JsonAlias({"zaxis"}) private SpaceAxisZ zAxis;

    // ================== [Y] 纬度维度 ==================
    private double yOrigin; private double yEnd;
    @JsonProperty("yResolution") @JsonAlias({"yresolution"}) private Double yResolution;
    @JsonProperty("yAxis") @JsonAlias({"yaxis"}) private SpaceAxisY yAxis;

    // ================== [X] 经度维度 ==================
    private double xOrigin; private double xEnd;
    @JsonProperty("xResolution") @JsonAlias({"xresolution"}) private Double xResolution;
    @JsonProperty("xAxis") @JsonAlias({"xaxis"}) private SpaceAxisX xAxis;

    // 🚀 给 Spring Boot Jackson 留的无参构造
    public TSShell() {}

    // 🚀 终极解析器：解决 1640998800 秒级时间戳被当成毫秒（1970年）的致命漏洞
    @JsonSetter("torigin")
    public void setTOriginFromJson(Object val) { this.tOrigin = parseInstant(val); }

    @JsonSetter("tend")
    public void setTEndFromJson(Object val) { this.tEnd = parseInstant(val); }

    private Instant parseInstant(Object val) {
        if (val == null) return null;
        if (val instanceof Number) {
            long l = ((Number) val).longValue();
            // 小于 300 亿，认定为秒级时间戳，否则为毫秒级
            return l < 30000000000L ? Instant.ofEpochSecond(l) : Instant.ofEpochMilli(l);
        } else if (val instanceof String) {
            return Instant.parse((String) val);
        }
        return null;
    }

    private TSShell(Builder builder) {
        this.featureId = builder.featureId;
        this.tOrigin = builder.tOrigin; this.tEnd = builder.tEnd; this.tResolution = builder.tResolution; this.tAxis = builder.tAxis;
        this.zOrigin = builder.zOrigin; this.zEnd = builder.zEnd; this.zResolution = builder.zResolution; this.zAxis = builder.zAxis;
        this.yOrigin = builder.yOrigin; this.yEnd = builder.yEnd; this.yResolution = builder.yResolution; this.yAxis = builder.yAxis;
        this.xOrigin = builder.xOrigin; this.xEnd = builder.xEnd; this.xResolution = builder.xResolution; this.xAxis = builder.xAxis;
    }

    public void upDateNew() {
        if (tOrigin != null && tEnd != null && tResolution != null) {
            long stepSeconds = tResolution.longValue();
            this.tOrigin = this.tOrigin.plusSeconds(stepSeconds);
            this.tEnd = this.tEnd.plusSeconds(stepSeconds);
        }
    }

    public boolean hasTime() { return tAxis != null; }
    public boolean hasZ() { return zAxis != null; }
    public boolean hasSpace() { return yAxis != null && xAxis != null; }

    public static class Builder {
        private final int featureId;
        private Instant tOrigin; private Instant tEnd; private Double tResolution; private TimeAxis tAxis;
        private double zOrigin = 0.0; private double zEnd = 0.0; private Double zResolution; private SpaceAxisZ zAxis;
        private double yOrigin = 0.0; private double yEnd = 0.0; private Double yResolution; private SpaceAxisY yAxis;
        private double xOrigin = 0.0; private double xEnd = 0.0; private Double xResolution; private SpaceAxisX xAxis;

        public Builder(int featureId) { this.featureId = featureId; }
        public Builder time(Instant origin, TimeAxis axis) {
            this.tOrigin = origin; this.tAxis = axis;
            if (axis != null) { this.tResolution = axis.getResolution(); this.tEnd = origin.plusSeconds((long)(axis.getResolution() * axis.getCount())); }
            return this;
        }
        public Builder z(double origin, SpaceAxisZ axis) {
            this.zOrigin = origin; this.zAxis = axis;
            if (axis != null) { this.zResolution = axis.getResolution(); this.zEnd = origin + (axis.getResolution() * axis.getCount()); }
            return this;
        }
        public Builder y(double origin, SpaceAxisY axis) {
            this.yOrigin = origin; this.yAxis = axis;
            if (axis != null) { this.yResolution = axis.getResolution(); this.yEnd = origin + (axis.getResolution() * axis.getCount()); }
            return this;
        }
        public Builder x(double origin, SpaceAxisX axis) {
            this.xOrigin = origin; this.xAxis = axis;
            if (axis != null) { this.xResolution = axis.getResolution(); this.xEnd = origin + (axis.getResolution() * axis.getCount()); }
            return this;
        }
        public TSShell build() {
            int currentDimIndex = 0;
            if (this.tAxis != null) this.tAxis.setDimensionIndex(currentDimIndex++);
            currentDimIndex++;
            if (this.zAxis != null) this.zAxis.setDimensionIndex(currentDimIndex++);
            if (this.yAxis != null) this.yAxis.setDimensionIndex(currentDimIndex++);
            if (this.xAxis != null) this.xAxis.setDimensionIndex(currentDimIndex++);
            return new TSShell(this);
        }
    }

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

    // 给非 JSON 手动调用留的 Setter
    public void setTOrigin(Instant tOrigin) { this.tOrigin = tOrigin; }
    public void setTEnd(Instant tEnd) { this.tEnd = tEnd; }
}