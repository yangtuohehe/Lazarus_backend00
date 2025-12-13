package com.example.lazarus_backend00.infrastructure.persistence.entity;

import java.time.LocalDateTime;

/**
 * parameter 表数据库映射对象
 * geometry 字段使用 WKT（String）
 * tensor_order 使用 JSON 字符串存储
 */
public class ParameterEntity {

    private Integer parameterId;
    private Integer interfaceId;
    private String ioType;

    private Integer temporalResolutionValue;
    private String temporalResolutionUnit;
    private Integer temporalRangeValue;
    private String temporalRangeUnit;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** geometry(Point,4326) → WKT，如 "POINT(120.123 23.456)" */
    private String originPoint;

    private Integer rowCount;
    private Integer columnCount;
    private Integer zCount;

    private Double spatialResolutionX;
    private Double spatialResolutionY;
    private Double spatialResolutionZ;
    private String spatialResolutionUnit;

    /** geometry(Geometry,4326) → WKT，可存 POINT / POLYGON / POLYGONZ */
    private String coverageGeom;

    /** JSONB: tensor dimension order */
    private String tensorOrder;

    // -------- getter / setter --------

    public Integer getParameterId() { return parameterId; }
    public void setParameterId(Integer parameterId) { this.parameterId = parameterId; }

    public Integer getInterfaceId() { return interfaceId; }
    public void setInterfaceId(Integer interfaceId) { this.interfaceId = interfaceId; }

    public String getIoType() { return ioType; }
    public void setIoType(String ioType) { this.ioType = ioType; }

    public Integer getTemporalResolutionValue() { return temporalResolutionValue; }
    public void setTemporalResolutionValue(Integer temporalResolutionValue) { this.temporalResolutionValue = temporalResolutionValue; }

    public String getTemporalResolutionUnit() { return temporalResolutionUnit; }
    public void setTemporalResolutionUnit(String temporalResolutionUnit) { this.temporalResolutionUnit = temporalResolutionUnit; }

    public Integer getTemporalRangeValue() { return temporalRangeValue; }
    public void setTemporalRangeValue(Integer temporalRangeValue) { this.temporalRangeValue = temporalRangeValue; }

    public String getTemporalRangeUnit() { return temporalRangeUnit; }
    public void setTemporalRangeUnit(String temporalRangeUnit) { this.temporalRangeUnit = temporalRangeUnit; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getOriginPoint() { return originPoint; }
    public void setOriginPoint(String originPoint) { this.originPoint = originPoint; }

    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }

    public Integer getColumnCount() { return columnCount; }
    public void setColumnCount(Integer columnCount) { this.columnCount = columnCount; }

    public Integer getZCount() { return zCount; }
    public void setZCount(Integer zCount) { this.zCount = zCount; }

    public Double getSpatialResolutionX() { return spatialResolutionX; }
    public void setSpatialResolutionX(Double spatialResolutionX) { this.spatialResolutionX = spatialResolutionX; }

    public Double getSpatialResolutionY() { return spatialResolutionY; }
    public void setSpatialResolutionY(Double spatialResolutionY) { this.spatialResolutionY = spatialResolutionY; }

    public Double getSpatialResolutionZ() { return spatialResolutionZ; }
    public void setSpatialResolutionZ(Double spatialResolutionZ) { this.spatialResolutionZ = spatialResolutionZ; }

    public String getSpatialResolutionUnit() { return spatialResolutionUnit; }
    public void setSpatialResolutionUnit(String spatialResolutionUnit) { this.spatialResolutionUnit = spatialResolutionUnit; }

    public String getCoverageGeom() { return coverageGeom; }
    public void setCoverageGeom(String coverageGeom) { this.coverageGeom = coverageGeom; }

    public String getTensorOrder() { return tensorOrder; }
    public void setTensorOrder(String tensorOrder) { this.tensorOrder = tensorOrder; }
}
