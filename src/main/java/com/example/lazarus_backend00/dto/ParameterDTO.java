package com.example.lazarus_backend00.dto;

import lombok.Data;
import java.util.List;


public class ParameterDTO {
    // 基础属性
    private String ioType;
    private Integer tensorOrder;

    // 空间原点信息
    private Double originPointLon;
    private Double originPointLat;
    private Double originPointAlt;

    // 嵌套结构
    private List<AxisDTO> axis;
    private List<FeatureDTO> features;
    // 新增：时间原点偏移量
    private Integer oTimeStep;
    public String getIoType() {
        return ioType;
    }

    public Integer getoTimeStep() {
        return oTimeStep;
    }

    public void setoTimeStep(Integer oTimeStep) {
        this.oTimeStep = oTimeStep;
    }

    public void setIoType(String ioType) {
        this.ioType = ioType;
    }

    public Integer getTensorOrder() {
        return tensorOrder;
    }

    public void setTensorOrder(Integer tensorOrder) {
        this.tensorOrder = tensorOrder;
    }

    public Double getOriginPointLon() {
        return originPointLon;
    }

    public void setOriginPointLon(Double originPointLon) {
        this.originPointLon = originPointLon;
    }

    public Double getOriginPointLat() {
        return originPointLat;
    }

    public void setOriginPointLat(Double originPointLat) {
        this.originPointLat = originPointLat;
    }

    public Double getOriginPointAlt() {
        return originPointAlt;
    }

    public void setOriginPointAlt(Double originPointAlt) {
        this.originPointAlt = originPointAlt;
    }

    public List<AxisDTO> getAxis() {
        return axis;
    }

    public void setAxis(List<AxisDTO> axis) {
        this.axis = axis;
    }

    public List<FeatureDTO> getFeatures() {
        return features;
    }

    public void setFeatures(List<FeatureDTO> features) {
        this.features = features;
    }
}