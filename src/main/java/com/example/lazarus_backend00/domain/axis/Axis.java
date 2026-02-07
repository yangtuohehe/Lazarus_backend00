package com.example.lazarus_backend00.domain.axis;

/**
 * 轴基类
 */
public abstract class Axis {

    /** 维度位置 (新增属性：表示该轴在数据中的维度索引，如 0, 1, 2) */
    protected Integer dimensionIndex;

    /** 行数/点数 */
    protected Integer count;

    /** 分辨率/步长 */
    protected Double resolution;

    /** 分辨率单位 */
    protected String unit;

    // -------- getter / setter --------

    public Integer getDimensionIndex() {
        return dimensionIndex;
    }

    public void setDimensionIndex(Integer dimensionIndex) {
        this.dimensionIndex = dimensionIndex;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Double getResolution() {
        return resolution;
    }

    public void setResolution(Double resolution) {
        this.resolution = resolution;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    protected Axis() {
        // 给 MyBatis / 子类用
    }
}