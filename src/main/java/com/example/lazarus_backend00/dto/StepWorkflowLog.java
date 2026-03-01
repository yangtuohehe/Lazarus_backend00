package com.example.lazarus_backend00.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class StepWorkflowLog {
    public Instant stepTime;                               // 本次步进的时间
    public List<String> ingestedData = new ArrayList<>();  // 1. 增加了哪些基础数据
    public List<String> sentTSShells = new ArrayList<>();  // 2. 发送了哪些 TSShell
    public Object triggerStateBefore;                      // 3. 触发器表状态 (触发前)
    public List<String> triggeredModels = new ArrayList<>();// 4. 触发了哪些模型
    public Object triggerStateAfter;                       // 5. 触发器表状态 (触发后)
    public List<String> writtenBackData = new ArrayList<>();// 6. 最终写回了哪些结果数据
}