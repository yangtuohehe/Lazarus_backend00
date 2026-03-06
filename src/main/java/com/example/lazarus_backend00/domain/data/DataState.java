package com.example.lazarus_backend00.domain.data;

/**
 * 空间网格数据的三元逻辑状态
 */
/**
 * 空间网格数据的三元逻辑状态
 */
public enum DataState {
    WAITING(0),    // 等待态：数据缺失 (通常隐式表达)
    READY(1),      // 就绪态：常规补齐生成的数据
    REPLACED(2);   // 替换态：被实测或同化覆写的数据

    private final int code;
    DataState(int code) { this.code = code; }
    public int getCode() { return code; }
}