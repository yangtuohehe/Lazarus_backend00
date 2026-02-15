package com.example.lazarus_backend00.dto.subdto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 数据状态检查结果 (DTO)
 * 用于在 Controller 和 Service 之间传递数据状态
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataCheckResult {

    private int featureId;      // 特征 ID

    private Instant timestamp;  // 具体时刻

    /**
     * 数据状态位：
     * 0: 空缺 (Missing)
     * 1: 实测数据 (Measured) - 优先级最高，无后缀
     * 2: 模拟数据 (Simulated) - 优先级次之，后缀 -ls
     */
    private int status;

    private String fileName;    // 实际找到的文件名 (方便调试或前端展示)
}