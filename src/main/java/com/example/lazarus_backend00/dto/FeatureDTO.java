package com.example.lazarus_backend00.dto;

import lombok.Data;


public class FeatureDTO {
    private String featureName;

    public String getFeatureName() {
        return featureName;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }
}