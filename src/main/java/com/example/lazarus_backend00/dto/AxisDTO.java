package com.example.lazarus_backend00.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;


@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true // 🔥 关键：设为 true，这样 type 字段才有值，Controller 才能读到
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TimeAxisDTO.class, name = "TIME"),
        @JsonSubTypes.Type(value = SpaceAxisXDTO.class, name = "SPACE_X"),
        @JsonSubTypes.Type(value = SpaceAxisXDTO.class, name = "X"),
        @JsonSubTypes.Type(value = SpaceAxisYDTO.class, name = "SPACE_Y"),
        @JsonSubTypes.Type(value = SpaceAxisYDTO.class, name = "Y"),
        @JsonSubTypes.Type(value = SpaceAxisZDTO.class, name = "SPACE_Z"),
        @JsonSubTypes.Type(value = SpaceAxisZDTO.class, name = "Z")
})
public abstract class AxisDTO {

    // 配合 visible=true，让 Controller 可以调用 getType()
    private String type;

    private Integer dimensionIndex;
    private Integer count;
    private Double resolution;
    private String unit;

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
}