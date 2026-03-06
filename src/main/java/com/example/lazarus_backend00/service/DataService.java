package com.example.lazarus_backend00.service;

import com.example.lazarus_backend00.domain.data.TSDataBlock;
import com.example.lazarus_backend00.domain.data.TSState;
import com.example.lazarus_backend00.domain.data.TSShell;
import java.util.List;

public interface DataService {

    // 🔥 修改点：参数改为 List<TSState>
    void notifyDataArrivals(List<TSState> incomingStates);

    void pushData(int featureId, TSDataBlock dataBlock);

    TSDataBlock fetchData(TSShell requirementShell);
}