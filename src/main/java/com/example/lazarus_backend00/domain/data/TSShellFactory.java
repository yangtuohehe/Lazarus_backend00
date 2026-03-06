package com.example.lazarus_backend00.domain.data;

import com.example.lazarus_backend00.domain.axis.Axis;
import com.example.lazarus_backend00.component.container.Parameter;
import com.example.lazarus_backend00.domain.axis.SpaceAxisX;
import com.example.lazarus_backend00.domain.axis.SpaceAxisY;
import com.example.lazarus_backend00.domain.axis.SpaceAxisZ;
import com.example.lazarus_backend00.domain.axis.TimeAxis;

import java.time.Instant;
import java.util.List;

/**
 * 大一统时空对象制造工厂
 * 职责：统一将静态的 Parameter 图纸实例化为运行时的 TSShell、TSState 或 TSDataBlock。
 */
public class TSShellFactory {

    // ===============================================================
    // 1. 基础产品：纯净外壳 (TSShell)
    // ===============================================================
    public static TSShell createFromParameter(int featureId, Instant initialTime, Parameter parameter) {
        TSShell.Builder builder = new TSShell.Builder(featureId);
        double xOrigin = parameter.getOriginPoint().getCoordinate().getX();
        double yOrigin = parameter.getOriginPoint().getCoordinate().getY();
        double zOrigin = Double.isNaN(parameter.getOriginPoint().getCoordinate().getZ())
                ? 0.0 : parameter.getOriginPoint().getCoordinate().getZ();

        List<Axis> axisList = parameter.getAxisList();
        if (axisList != null) {
            for (Axis axis : axisList) {
                if (axis instanceof TimeAxis) builder.time(initialTime, (TimeAxis) axis);
                else if (axis instanceof SpaceAxisZ) builder.z(zOrigin, (SpaceAxisZ) axis);
                else if (axis instanceof SpaceAxisY) builder.y(yOrigin, (SpaceAxisY) axis);
                else if (axis instanceof SpaceAxisX) builder.x(xOrigin, (SpaceAxisX) axis);
            }
        }
        return builder.build();
    }

    // ===============================================================
    // 2. 状态产品：带状态的外壳 (TSState)
    // ===============================================================
    public static TSState createTSStateFromParameter(int featureId, Instant initialTime, Parameter parameter, DataState dataState) {
        TSShell baseShell = createFromParameter(featureId, initialTime, parameter);
        return new TSState(baseShell, dataState);
    }

    public static TSState createTSStateFromShell(TSShell baseShell, DataState dataState) {
        return new TSState(baseShell, dataState);
    }

    // ===============================================================
    // 3. 数据产品：合并了原本 TSDataBlockFactory 的能力
    // ===============================================================
    /**
     * 根据图纸生成一个带有空数据数组 (全0) 的 TSDataBlock
     * 适用场景：数据预加载前的占位符初始化
     */
    public static TSDataBlock createEmptyTSDataBlockFromParameter(int featureId, Instant initialTime, Parameter parameter) {
        TSShell baseShell = createFromParameter(featureId, initialTime, parameter);

        // 动态计算需要的数组容量
        int t = (baseShell.getTAxis() != null) ? baseShell.getTAxis().getCount() : 1;
        int z = (baseShell.getZAxis() != null) ? baseShell.getZAxis().getCount() : 1;
        int y = (baseShell.getYAxis() != null) ? baseShell.getYAxis().getCount() : 1;
        int x = (baseShell.getXAxis() != null) ? baseShell.getXAxis().getCount() : 1;

        float[] emptyData = new float[t * z * y * x];

        // 构建 DataBlock (BatchSize 默认为 1)
        return new TSDataBlock(baseShell, emptyData, 1, null);
    }
}