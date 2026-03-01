//package com.example.lazarus_backend00.controller;
//
//import com.example.lazarus_backend00.service.WorkflowLoggerService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//@RequestMapping("/api/log")
//public class LogController {
//
//    @Autowired
//    private WorkflowLoggerService loggerService;
//
//    // 前端一调用这个接口，立刻把内存里的日志写进本地文件！
//    @PostMapping("/flush")
//    public String flush() {
//        loggerService.flushToJson();
//        return "✅ 日志已强制落盘！";
//    }
//}