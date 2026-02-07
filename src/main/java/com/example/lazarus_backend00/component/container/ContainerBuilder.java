package com.example.lazarus_backend00.component.container;

import java.util.List;

@FunctionalInterface
public interface ContainerBuilder {
    // 定义了一个标准动作：给我这些参数，我给你一个 ModelContainer
    ModelContainer build(int containerId, String version, String modelFilePath, List<Parameter> parameterList);
}
//随笔：容器构建器接口 (辅助工具)
//因为创建模型容器需要传入 ID、路径等参数，我们需要先定义一个函数式接口，用来指代“创建模型容器的动作”。