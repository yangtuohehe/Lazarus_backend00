package com.example.lazarus_backend00.component;

import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.service.WorkflowLoggerService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;

@Aspect
@Component
public class WorkflowLoggingAspect {

    @Autowired
    private WorkflowLoggerService loggerService;

    // 1. 拦截时钟步进，开始记录
    @Before("@annotation(com.example.lazarus_backend00.annotation.AuditAnnotations.LogStepStart)")
    public void onStepStart(JoinPoint joinPoint) {
        // 假设重置时间的参数中，第一个参数是 Instant
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Instant) {
                loggerService.startNewStep((Instant) arg);
                break;
            }
        }
    }

    // 2. 拦截新增数据
    @Before("@annotation(com.example.lazarus_backend00.annotation.AuditAnnotations.LogDataIngest)")
    public void onDataIngest(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        // 根据你的方法签名提取：pushData(int featureId, TSDataBlock block)
        if (args.length >= 2 && args[1] instanceof TSDataBlock) {
            TSDataBlock block = (TSDataBlock) args[1];
            loggerService.logIngested("FeatureID: " + args[0] + ", Time: " + block.getTOrigin());
        }
    }

    // 3. 拦截发送 TSShell
    @Before("@annotation(com.example.lazarus_backend00.annotation.AuditAnnotations.LogTSShellSent)")
    public void onTSShellSent(JoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof TSShell) {
                loggerService.logSentShell("Sent Shell -> FeatureID: " + ((TSShell) arg).getFeatureId());
            }
        }
    }

    // 4. 🔥 核心：包裹触发器检查，同时获取执行前和执行后的状态
    @Around("@annotation(com.example.lazarus_backend00.annotation.AuditAnnotations.LogTriggerProcess)")
    public Object onTriggerProcess(ProceedingJoinPoint joinPoint) throws Throwable {
        Object target = joinPoint.getTarget();

        // 使用反射调用你的 TriggerRegistry 中的获取状态的方法（假设叫 getTriggerState）
        try {
            Method getStateMethod = target.getClass().getMethod("getTriggerState");
            loggerService.logTriggerBefore(getStateMethod.invoke(target));
        } catch (Exception e) {
            loggerService.logTriggerBefore("无法获取触发前状态: 请确保该类有 getTriggerState() 方法");
        }

        Object result = joinPoint.proceed(); // 执行真正的触发器业务逻辑！

        // 再次获取执行后的状态
        try {
            Method getStateMethod = target.getClass().getMethod("getTriggerState");
            loggerService.logTriggerAfter(getStateMethod.invoke(target));
        } catch (Exception e) {
            loggerService.logTriggerAfter("无法获取触发后状态");
        }
        return result;
    }

    // 5. 拦截模型唤醒 (由于不知你的 Task 类型，简单打印类名和参数)
    @Before("@annotation(com.example.lazarus_backend00.annotation.AuditAnnotations.LogModelTriggered)")
    public void onModelTriggered(JoinPoint joinPoint) {
        if (joinPoint.getArgs().length > 0) {
            loggerService.logTriggeredModel("Dispatched Task: " + joinPoint.getArgs()[0].toString());
        }
    }

    // 6. 拦截结果落盘，并在这里触发 JSON 保存
    @AfterReturning("@annotation(com.example.lazarus_backend00.annotation.AuditAnnotations.LogResultWriteBack)")
    public void onResultWriteBack(JoinPoint joinPoint) {
        // 先尝试记录数据
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof TSDataBlock) {
            TSDataBlock block = (TSDataBlock) args[0];
            loggerService.logWrittenBack("FeatureID: " + block.getFeatureId() + " 已落盘");
        } else {
            loggerService.logWrittenBack("探测到了结果落盘动作，但参数不是 TSDataBlock: " + joinPoint.getSignature().getName());
        }

        // 🔥 无脑触发保存！
        loggerService.flushToJson();
    }
}