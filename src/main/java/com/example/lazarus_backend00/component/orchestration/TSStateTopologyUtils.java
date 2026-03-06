package com.example.lazarus_backend00.component.orchestration;

import com.example.lazarus_backend00.domain.data.DataState;
import com.example.lazarus_backend00.domain.data.TSShell;
import com.example.lazarus_backend00.domain.data.TSState;
import org.locationtech.jts.geom.Envelope;
import java.util.BitSet;

/**
 * 栅格拓扑与状态映射工具类
 */
public class TSStateTopologyUtils {

    /**
     * 根据目标外壳 (Target Shell) 的要求，从源数据湖 (Source State) 中裁剪出对应区域的状态。
     * 严谨使用地理投影原点与分辨率进行格点映射。
     */
    public static TSState extractSubRegion(TSState sourceState, TSShell targetShell) {
        // 1. 初始化一张基于目标需求的纯空白画板
        TSState targetState = new TSState(targetShell, DataState.WAITING);

        // 如果源数据本身连位图都没激活，直接返回空板
        if (sourceState.getReadyMask().isEmpty() && sourceState.getReplacedMask().isEmpty()) {
            return targetState;
        }

        // 2. 利用 JTS 快速求包络线交集 (粗筛)
        Envelope sourceEnv = sourceState.getEnvelope();
        Envelope targetEnv = targetShell.getEnvelope();
        Envelope intersectEnv = sourceEnv.intersection(targetEnv);

        // 如果完全不相交，直接返回空板
        if (intersectEnv.isNull()) {
            return targetState;
        }

        // 3. 提取目标(局部)网格的物理参数
        int tWidth = targetState.getWidth();
        int tHeight = targetState.getHeight();
        double tx0 = targetShell.getXOrigin();
        double ty0 = targetShell.getYOrigin();
        double txRes = targetShell.getXResolution() != null ? targetShell.getXResolution() : 1.0;
        double tyRes = targetShell.getYResolution() != null ? targetShell.getYResolution() : 1.0;

        // 4. 提取源(大图)网格的物理参数
        int sWidth = sourceState.getWidth();
        int sHeight = sourceState.getHeight();
        double sx0 = sourceState.getXOrigin();
        double sy0 = sourceState.getYOrigin();
        double sxRes = sourceState.getXResolution() != null ? sourceState.getXResolution() : 1.0;
        double syRes = sourceState.getYResolution() != null ? sourceState.getYResolution() : 1.0;

        BitSet srcReady = sourceState.getReadyMask();
        BitSet srcReplaced = sourceState.getReplacedMask();
        BitSet tgtReady = targetState.getReadyMask();
        BitSet tgtReplaced = targetState.getReplacedMask();

        // 5. 遍历目标网格的像元，执行严谨的坐标到坐标的“重采样映射”
        for (int tr = 0; tr < tHeight; tr++) {
            // 计算目标网格当前行的地理 Y 坐标
            double y = ty0 + tr * tyRes;

            // 如果这个坐标超出了交集区域，跳过
            if (y < intersectEnv.getMinY() || y > intersectEnv.getMaxY()) continue;

            // 根据地理 Y 坐标，反算在源网格中的行索引
            int sr = (int) Math.round((y - sy0) / syRes);
            if (sr < 0 || sr >= sHeight) continue;

            for (int tc = 0; tc < tWidth; tc++) {
                // 计算目标网格当前列的地理 X 坐标
                double x = tx0 + tc * txRes;

                // 如果坐标超出交集区域，跳过
                if (x < intersectEnv.getMinX() || x > intersectEnv.getMaxX()) continue;

                // 反算源网格列索引
                int sc = (int) Math.round((x - sx0) / sxRes);
                if (sc < 0 || sc >= sWidth) continue;

                // 物理地址对应上了，执行一维数组索引映射
                int tgtIdx = tr * tWidth + tc;
                int srcIdx = sr * sWidth + sc;

                // 拷贝状态位
                if (srcReady.get(srcIdx)) tgtReady.set(tgtIdx);
                if (srcReplaced.get(srcIdx)) tgtReplaced.set(tgtIdx);
            }
        }

        return targetState;
    }
}