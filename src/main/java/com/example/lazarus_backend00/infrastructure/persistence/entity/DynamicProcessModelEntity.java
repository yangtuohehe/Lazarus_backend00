package com.example.lazarus_backend00.infrastructure.persistence.entity;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 数据表 dynamic_process_model 的数据库映射对象
 */
public class DynamicProcessModelEntity {

    // 1. 符合 Java 规范的小写 id
    private Integer id;

    private String modelName;
    private String modelSourcePaper;
    private String modelAuthor;
    private String modelSummary;
    private Integer version;

    // 2. 修改：支持 ONNX 文件存储
    private byte[] modelFile;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;

    // -------- getter / setter (标准命名) --------

    // 修改：getId 代替 getProcessmodelId
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelSourcePaper() {
        return modelSourcePaper;
    }

    public void setModelSourcePaper(String modelSourcePaper) {
        this.modelSourcePaper = modelSourcePaper;
    }

    public String getModelAuthor() {
        return modelAuthor;
    }

    public void setModelAuthor(String modelAuthor) {
        this.modelAuthor = modelAuthor;
    }

    public String getModelSummary() {
        return modelSummary;
    }

    public void setModelSummary(String modelSummary) {
        this.modelSummary = modelSummary;
    }

    public byte[] getModelFile() {
        return modelFile;
    }

    public void setModelFile(byte[] modelFile) {
        this.modelFile = modelFile;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}