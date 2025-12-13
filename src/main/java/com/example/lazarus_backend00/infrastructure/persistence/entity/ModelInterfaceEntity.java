package com.example.lazarus_backend00.infrastructure.persistence.entity;

import java.time.LocalDateTime;

/**
 * 数据表 model_interface 的数据库映射对象
 */
public class ModelInterfaceEntity {

    /** model_interface.interface_id */
    private Integer interfaceId;

    /** model_interface.processmodel_id */
    private Integer processmodelId;

    /** model_interface.interface_name */
    private String interfaceName;

    /** model_interface.interface_type */
    private String interfaceType;

    /** model_interface.is_default */
    private Boolean isDefault;

    /** model_interface.created_at */
    private LocalDateTime createdAt;

    /** model_interface.updated_at */
    private LocalDateTime updatedAt;

    /** model_interface.interface_summary (jsonb) */
    private String interfaceSummary;

    // -------- getter / setter --------

    public Integer getInterfaceId() {
        return interfaceId;
    }

    public void setInterfaceId(Integer interfaceId) {
        this.interfaceId = interfaceId;
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

    public String getInterfaceType() {
        return interfaceType;
    }

    public void setInterfaceType(String interfaceType) {
        this.interfaceType = interfaceType;
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
