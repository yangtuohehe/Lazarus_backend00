package com.example.lazarus_backend00.infrastructure.persistence.entity;

import org.locationtech.jts.geom.Geometry;
import java.time.LocalDateTime;

public class ParameterEntity {

    // 对应 parameter_id (主键)
    private Integer id;

    private Integer interfaceId;
    private String ioType;

    // JTS Geometry 对象
    private Geometry originPoint;
    private Geometry coverageGeom;

    // ✅ 修改：类型改为 Integer，表示这是第几个张量 (0, 1, 2...)
    private Integer tensorOrder;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // 新增：时间原点偏移量（时间步）
    private Integer oTimeStep;
    // -------- getter / setter --------

    public Integer getoTimeStep() {
        return oTimeStep;
    }

    public void setoTimeStep(Integer oTimeStep) {
        this.oTimeStep = oTimeStep;
    }

    public Integer getId() {
        return id;
    }
    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getInterfaceId() { return interfaceId; }
    public void setInterfaceId(Integer interfaceId) { this.interfaceId = interfaceId; }

    public String getIoType() { return ioType; }
    public void setIoType(String ioType) { this.ioType = ioType; }

    public Geometry getOriginPoint() { return originPoint; }
    public void setOriginPoint(Geometry originPoint) { this.originPoint = originPoint; }

    public Geometry getCoverageGeom() { return coverageGeom; }
    public void setCoverageGeom(Geometry coverageGeom) { this.coverageGeom = coverageGeom; }

    // ✅ 修改 Getter：返回 Integer
    public Integer getTensorOrder() {
        return tensorOrder;
    }

    // ✅ 修改 Setter：接收 Integer
    public void setTensorOrder(Integer tensorOrder) {
        this.tensorOrder = tensorOrder;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}