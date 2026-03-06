package com.example.lazarus_backend00.domain.axis;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 轴基类
 * 🚨 新增：Jackson 多态反序列化配置
 * 作用：让 Spring Boot 知道在碰到 List<Axis> 时，具体该实例化哪个子类。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TimeAxis.class, name = "TIME"),       // 对应 JSON 里的 "type":"TIME"
        @JsonSubTypes.Type(value = SpaceAxisX.class, name = "SPACE_X"),  // 对应 JSON 里的 "type":"SPACE_X"
        @JsonSubTypes.Type(value = SpaceAxisY.class, name = "SPACE_Y"),  // 对应 JSON 里的 "type":"SPACE_Y"
        @JsonSubTypes.Type(value = SpaceAxisZ.class, name = "SPACE_Z")
})
public abstract class Axis {

    // (如果你原本 JSON 里没有 type 这个字段，可以加一个让前端传，或者数据库里有的话会自动映射)
    protected String type;

    /** 维度位置 (新增属性：表示该轴在数据中的维度索引，如 0, 1, 2) */
    protected Integer dimensionIndex;

    /** 行数/点数 */
    protected Integer count;

    /** 分辨率/步长 */
    protected Double resolution;

    /** 分辨率单位 */
    protected String unit;

    // -------- getter / setter --------

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

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