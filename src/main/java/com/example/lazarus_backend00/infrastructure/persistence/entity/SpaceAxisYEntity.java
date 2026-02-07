package com.example.lazarus_backend00.infrastructure.persistence.entity;

import com.example.lazarus_backend00.domain.axis.Axis;

import java.time.LocalDateTime;

public class SpaceAxisYEntity extends Axis {

    private Integer id;

    private Integer parameterId;


    /** model_runtime_env.created_at */
    private LocalDateTime createdAt;
    /** model_runtime_env.updated_at */
    private LocalDateTime updatedAt;

    public Integer getParameterId() {
        return parameterId;
    }

    public void setParameterId(Integer parameterId) {
        this.parameterId = parameterId;
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

    public SpaceAxisYEntity() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}