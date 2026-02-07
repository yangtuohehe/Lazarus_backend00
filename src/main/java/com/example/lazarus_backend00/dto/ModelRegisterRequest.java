package com.example.lazarus_backend00.dto;

import lombok.Data;


public class ModelRegisterRequest {

    // 1. 修改变量名为驼峰 (dynamicprocessmodel -> dynamicProcessModel)
    private DynamicProcessModelDTO dynamicProcessModel;

    // 2. 修改变量名为驼峰 (modelinterface -> modelInterface)
    // 🔥 只有改成这样，Controller 里的 getModelInterface() 才能用！
    private ModelInterfaceDTO modelInterface;

    public DynamicProcessModelDTO getDynamicProcessModel() {
        return dynamicProcessModel;
    }

    public void setDynamicProcessModel(DynamicProcessModelDTO dynamicProcessModel) {
        this.dynamicProcessModel = dynamicProcessModel;
    }

    public ModelInterfaceDTO getModelInterface() {
        return modelInterface;
    }

    public void setModelInterface(ModelInterfaceDTO modelInterface) {
        this.modelInterface = modelInterface;
    }
}