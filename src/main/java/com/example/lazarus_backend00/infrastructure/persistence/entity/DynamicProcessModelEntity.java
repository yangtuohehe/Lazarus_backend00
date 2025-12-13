package com.example.lazarus_backend00.infrastructure.persistence.entity;

import java.time.LocalDateTime;

/**
 * 数据表 dynamic_process_model 的数据库映射对象
 * 修改：model_filePath -> modelFile，类型改为 byte[] 支持 ONNX 文件存储
 */
public class DynamicProcessModelEntity {

    private Integer processmodelId;
    private String modelName;
    private String modelSourcePaper;
    private String modelAuthor;
    private String modelSummary;
    private byte[] modelFile;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // -------- getter / setter --------

    public Integer getProcessmodelId() {
        return processmodelId;
    }

    public void setProcessmodelId(Integer processmodelId) {
        this.processmodelId = processmodelId;
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
}
