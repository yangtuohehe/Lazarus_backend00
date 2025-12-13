package com.example.lazarus_backend00.infrastructure.persistence.entity;

import java.time.LocalDateTime;

/**
 * 数据表 model_runtime_env 的数据库映射对象
 */
public class ModelRuntimeEnvEntity {

    /** model_runtime_env.env_id */
    private Integer envId;

    /** model_runtime_env.system_env */
    private String systemEnv;

    /** model_runtime_env.library_env (jsonb) */
    private String libraryEnv;

    /** model_runtime_env.required_memory */
    private Integer requiredMemory;

    /** model_runtime_env.is_active */
    private Boolean isActive;

    /** model_runtime_env.created_at */
    private LocalDateTime createdAt;

    /** model_runtime_env.updated_at */
    private LocalDateTime updatedAt;

    // -------- getter / setter --------

    public Integer getEnvId() {
        return envId;
    }

    public void setEnvId(Integer envId) {
        this.envId = envId;
    }

    public String getSystemEnv() {
        return systemEnv;
    }

    public void setSystemEnv(String systemEnv) {
        this.systemEnv = systemEnv;
    }

    public String getLibraryEnv() {
        return libraryEnv;
    }

    public void setLibraryEnv(String libraryEnv) {
        this.libraryEnv = libraryEnv;
    }

    public Integer getRequiredMemory() {
        return requiredMemory;
    }

    public void setRequiredMemory(Integer requiredMemory) {
        this.requiredMemory = requiredMemory;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
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
