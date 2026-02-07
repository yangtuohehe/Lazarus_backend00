package com.example.lazarus_backend00.component.container;

/**
 * 模型容器状态
 */
public enum ContainerStatus {
    /**
     * 已创建：对象在堆内存中，但未加载 ONNX 文件到显存/堆外内存。
     */
    CREATED,

    /**
     * 已加载：资源就绪 (Session Created)，随时可进行推理。
     */
    LOADED,

    /**
     * 运行中：正在执行 run() 方法，此时不可卸载。
     */
    RUNNING,

    /**
     * 运行失败：最近一次推理抛出了异常，但容器本身可能还是好的。
     */
    FAILED,

    /**
     * 已卸载：资源已释放 (Session Closed)。
     */
    UNLOADED,

    /**
     * [新增] 错误状态：
     * 发生不可恢复的错误 (如加载失败、文件不存在、OOM)。
     * 处于此状态的容器不应再接受请求，除非重新注册。
     */
    ERROR
}