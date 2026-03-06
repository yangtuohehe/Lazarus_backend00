//package com.example.lazarus_backend00.service;
//
//import com.example.lazarus_backend00.dto.StepWorkflowLog;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.SerializationFeature;
//import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
//import org.springframework.stereotype.Service;
//
//import java.io.File;
//import java.io.IOException;
//import java.time.Instant;
//import java.time.ZoneId;
//import java.time.format.DateTimeFormatter;
//
//@Service
//public class WorkflowLoggerService {
//
//    // 🔥 修复：去除了 ThreadLocal，直接使用全局实例，跨 HTTP 请求也能共享这一个日志对象！
//    private StepWorkflowLog currentLog = new StepWorkflowLog();
//    private final ObjectMapper objectMapper;
//    private final String LOG_DIR = "log/";
//
//    public WorkflowLoggerService() {
//        this.objectMapper = new ObjectMapper();
//        this.objectMapper.registerModule(new JavaTimeModule());
//        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
//
//        File logDir = new File(LOG_DIR);
//        if (!logDir.exists()) {
//            boolean created = logDir.mkdirs();
//            System.out.println("📁 尝试创建 log 文件夹: " + (created ? "成功" : "失败"));
//        }
//    }
//
//    public void startNewStep(Instant stepTime) {
//        currentLog = new StepWorkflowLog(); // 每次新的步进，重新 new 一个干净的日志本
//        currentLog.stepTime = stepTime;
//        System.out.println("📝 [Logger] 开始记录新纪元: " + stepTime);
//    }
//
//    public void logIngested(String info) { currentLog.ingestedData.add(info); }
//    public void logSentShell(String info) { currentLog.sentTSShells.add(info); }
//    public void logTriggerBefore(Object state) { currentLog.triggerStateBefore = state; }
//    public void logTriggeredModel(String info) { currentLog.triggeredModels.add(info); }
//    public void logTriggerAfter(Object state) { currentLog.triggerStateAfter = state; }
//    public void logWrittenBack(String info) { currentLog.writtenBackData.add(info); }
//
//    public void flushToJson() {
//        if (currentLog.stepTime == null) {
//            System.err.println("⚠️ [Logger] 警告：尝试保存日志，但 stepTime 为空，说明 @LogStepStart 没有成功拦截到时间！");
//            // 即便为空也给个默认时间，强行存下来看看截到了什么！
//            currentLog.stepTime = Instant.now();
//        }
//
//        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault());
//        String filename = LOG_DIR + "workflow_" + formatter.format(currentLog.stepTime) + ".json";
//
//        try {
//            objectMapper.writeValue(new File(filename), currentLog);
//            System.out.println("✅ [审计日志] 成功！全链路监控 JSON 已保存至: " + new File(filename).getAbsolutePath());
//        } catch (IOException e) {
//            System.err.println("❌ 保存日志 JSON 失败: " + e.getMessage());
//        }
//    }
//}