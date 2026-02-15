package com.example.lazarus_backend00.dto.subdto;

import com.example.lazarus_backend00.domain.data.TSShell;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataUpdatePacket {
    /** 时空外壳 (目前固定为一个时间点) */
    private TSShell shell;

    /** * 数据状态
     * 1: 新增 (New) - 之前没有数据
     * 2: 替换 (Replace) - 之前有模拟数据，但实测数据偏差过大，需修正
     */
    private int status;
}