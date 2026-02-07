package com.example.lazarus_backend00.dto;

import lombok.Data;

public class DynamicProcessModelDTO {
    private String modelName;
    private String modelSourcePaper;
    private String modelAuthor;
    private String modelSummary;

    // 对应数据库的 INT 类型
    private Integer version;

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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}