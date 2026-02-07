package com.example.lazarus_backend00.infrastructure.persistence.entity;

public class FeatureParameterEntity {

    private Integer id;
    private Integer featureId;
    private Integer parameterId;
    private Integer featureLayer;

    /** 新增：描述特征在一个参数中所处的维度位置 (如: 0, 1, 2) */
    private Integer dimensionIndex;

    // -------- getter / setter --------

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getFeatureId() { return featureId; }
    public void setFeatureId(Integer featureId) { this.featureId = featureId; }

    public Integer getParameterId() { return parameterId; }
    public void setParameterId(Integer parameterId) { this.parameterId = parameterId; }

    public Integer getFeatureLayer() { return featureLayer; }
    public void setFeatureLayer(Integer featureLayer) { this.featureLayer = featureLayer; }

    // 新增 Getter/Setter
    public Integer getDimensionIndex() { return dimensionIndex; }
    public void setDimensionIndex(Integer dimensionIndex) { this.dimensionIndex = dimensionIndex; }
}