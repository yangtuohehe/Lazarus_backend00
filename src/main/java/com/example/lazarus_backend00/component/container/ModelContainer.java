package com.example.lazarus_backend00.component.container;

import com.example.lazarus_backend00.domain.data.TSDataBlock;

import java.util.List;

public interface ModelContainer {

    // ... (其他元数据和生命周期方法保持不变) ...
    int getContainerId();
    Integer getVersion();
    ContainerStatus getStatus();
    List<Parameter> getParameterList();
    boolean load();
    boolean unload();
    int getMemoryUsage();

    // ================== 核心推理业务 (针对单特征 Block 的适配修改) ==================

    /**
     * 执行模型推理
     *
     * 结构说明：
     * 由于 TSDataBlock 仅存储单特征，而模型的一个输入张量可能需要多特征组合 (Multi-Channel)，
     * 因此输入输出必须采用 "二维列表" 结构。
     *
     * @param inputGroups 输入数据组
     * - 外层 List 的索引 i : 对应模型定义的第 i 个输入张量 (Parameter.tensorOrder = i)
     * - 内层 List 的索引 j : 对应构成该张量的第 j 个特征 (Parameter.featureList[j])
     *
     * 例如：模型 Input[0] 需要 [温度, 湿度]，Input[1] 需要 [气压]
     * inputGroups.get(0) -> [ Block(温度), Block(湿度) ]
     * inputGroups.get(1) -> [ Block(气压) ]
     *
     * @return 输出数据组
     * - 外层 List 的索引 i : 对应模型定义的第 i 个输出张量
     * - 内层 List 的索引 j : 对应该输出张量拆解后的第 j 个特征
     *
     * 例如：模型 Output[0] 产出 [风速, 风向]
     * return.get(0) -> [ Block(风速), Block(风向) ]
     */
    List<List<TSDataBlock>> run(List<List<TSDataBlock>> inputGroups);
}