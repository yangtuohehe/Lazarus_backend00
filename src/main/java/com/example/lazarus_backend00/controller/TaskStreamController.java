package com.example.lazarus_backend00.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin
public class TaskStreamController {

    // 存放所有正在“收听广播”的前端客户端
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // 前端调用的订阅接口
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTasks() {
        SseEmitter emitter = new SseEmitter(0L); // 0L 表示永不超时
        emitters.add(emitter);

        // 客户端掉线时自动清理
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        return emitter;
    }

    // 提供给 Trigger 调用的广播方法
    public void broadcastTask(String taskInfo) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("newTask").data(taskInfo));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}