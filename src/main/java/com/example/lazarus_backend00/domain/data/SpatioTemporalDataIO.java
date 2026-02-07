package com.example.lazarus_backend00.domain.data;

import java.io.IOException;
import java.util.List;

/**
 * 时空数据 I/O 转换接口
 * 核心职责：桥接元数据(TSShell)与物理存储(NetCDF/数据库)，完成数据的加载与融合。
 */
public interface SpatioTemporalDataIO {

    /**
     * 功能 1：数据加载与融合 (Multi-Shell -> Single DataBlock)
     * * 业务逻辑：
     * 1. 遍历 inputShells 列表。
     * 2. 根据每个 Shell 的范围(T,Z,Y,X)和特征ID，去硬盘上的 NetCDF 或数据库中读取真实的 float[] 数组。
     * 3. 选取第一个 Shell 作为时空基底。
     * 4. 将读取到的多个 float[] 数组挂载到同一个 TSDataBlock 中，实现多要素融合。
     *
     * @param inputShells 需要加载数据的时空外壳列表 (通常直接传入 ExecutableTask.getInputs())
     * @return 满载真实多要素张量数据的 TSDataBlock，可直接送入 AI 模型。
     * @throws IOException 当读取底层 NetCDF 文件或数据库查询失败时抛出
     */
    TSDataBlock loadAndMerge(List<TSShell> inputShells) throws IOException;

    // TODO: 未来可拓展 功能 2 - 将模型吐出的 TSDataBlock 逆向写回 NetCDF 或数据库
    // void saveToStorage(TSDataBlock outputData, TSShell outputShell) throws IOException;
}