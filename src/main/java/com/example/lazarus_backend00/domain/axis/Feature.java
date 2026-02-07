package com.example.lazarus_backend00.domain.axis;

public class Feature {
    private Integer id;
    private String featureName;

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

    public Feature(Integer id, String featureName) {
        this.id = id;
        this.featureName = featureName;
    }
}
