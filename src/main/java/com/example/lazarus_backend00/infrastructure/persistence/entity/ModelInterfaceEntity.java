package com.example.lazarus_backend00.infrastructure.persistence.entity;

import java.time.LocalDateTime;

/**
 * 数据表 model_interface 的数据库映射对象
 */
public class ModelInterfaceEntity {

    /** 主键 id */
    private Integer id;

    /** model_interface.processmodel_id */
    private Integer processmodelId;

    /** model_interface.interface_name */
    private String interfaceName;

    /** model_interface.is_default */
    private Boolean isDefault;

    /** model_interface.created_at */
    private LocalDateTime createdAt;

    /** model_interface.updated_at */
    private LocalDateTime updatedAt;

    /** model_interface.interface_summary (jsonb) */
    private String interfaceSummary;

    // -------- getter / setter (标准命名) --------

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getProcessmodelId() {
        return processmodelId;
    }

    public void setProcessmodelId(Integer processmodelId) {
        this.processmodelId = processmodelId;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getInterfaceSummary() {
        return interfaceSummary;
    }

    public void setInterfaceSummary(String interfaceSummary) {
        this.interfaceSummary = interfaceSummary;
    }
}