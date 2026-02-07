package com.example.lazarus_backend00.infrastructure.persistence.entity;

import java.time.LocalDateTime;

/**
 * 数据表 feature 的数据库映射对象
 */
public class FeatureEntity {

    private Integer id;
    private String featureName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ---------- getter / setter (修正为标准命名) ----------

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getFeatureName() {
        return featureName;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
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
}