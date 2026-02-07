package com.example.lazarus_backend00.dto;

import lombok.Data;
import java.util.List;

public class ModelInterfaceDTO {
    private String interfaceName;
    private String interfaceSummary;

    // Lombok 会生成 getIsDefault() 和 setIsDefault()
    // Jackson 会自动识别 JSON 中的 "isDefault" 字段
    private Boolean isDefault;

    // 嵌套参数列表
    private List<ParameterDTO> parameters;

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getInterfaceSummary() {
        return interfaceSummary;
    }

    public void setInterfaceSummary(String interfaceSummary) {
        this.interfaceSummary = interfaceSummary;
    }

    public Boolean getDefault() {
        return isDefault;
    }

    public void setDefault(Boolean aDefault) {
        isDefault = aDefault;
    }

    public List<ParameterDTO> getParameters() {
        return parameters;
    }

    public void setParameters(List<ParameterDTO> parameters) {
        this.parameters = parameters;
    }
}