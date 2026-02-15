package com.example.lazarus_backend00.service.subservice;

import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.dto.subdto.DataCheckResult;

import java.util.List;

/**
 * 数据存储服务接口
 * 职责：负责底层 GeoTiff 文件的读写操作，屏蔽文件系统细节。
 */
public interface DataStorageService {

    /**
     * 检查数据状态
     * @param shells 查询请求外壳列表
     * @return 状态列表 (0:缺失, 1:实测, 2:模拟)
     */
    List<DataCheckResult> checkDataStatus(List<TSShell> shells);

    /**
     * 读取数据块
     * 策略：优先读取实测数据，若无则读取模拟数据。
     * @param shells 查询请求外壳列表
     * @return 包含实际数据的块列表
     */
    List<TSDataBlock> fetchDataBlocks(List<TSShell> shells);

    /**
     * 入库计算结果 (仿真数据)
     * 行为：强制添加 "-ls" 后缀保存。
     * @param block 包含数据的块
     */
    void ingestCalculatedData(TSDataBlock block);
}