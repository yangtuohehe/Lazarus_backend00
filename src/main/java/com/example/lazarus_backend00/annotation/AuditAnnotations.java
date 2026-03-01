package com.example.lazarus_backend00.annotation;

import java.lang.annotation.*;

public class AuditAnnotations {
    @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
    public @interface LogStepStart {} // 标记步进开始

    @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
    public @interface LogDataIngest {} // 标记数据汇入

    @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
    public @interface LogTSShellSent {} // 标记Shell发送

    @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
    public @interface LogTriggerProcess {} // 标记触发器处理 (核心：记录前后状态)

    @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
    public @interface LogModelTriggered {} // 标记模型被唤醒分发

    @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
    public @interface LogResultWriteBack {} // 标记结果落盘并结束流程
}