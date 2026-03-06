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

    List<DataCheckResult> checkDataStatus(List<TSShell> shells);

    List<TSDataBlock> fetchDataBlocks(List<TSShell> shells);

    // 🔥 匹配主系统单点查数协议
    TSDataBlock fetchSingleDataBlock(TSShell shell);

    void ingestCalculatedData(TSDataBlock block);
}