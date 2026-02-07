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
 * TSShell 制造工厂
 * 职责：负责将静态的 Parameter 或数据库配置，实例化为运行时的 TSShell 外壳。
 * 优势：将复杂的类型判断 (instanceof) 与实例化逻辑彻底隔离。
 */
public class TSShellFactory {

    /**
     * 根据模型的配置图纸 (Parameter)，生产对应的时空外壳
     * @param featureId 该数据流代表的特征ID (如 101)
     * @param initialTime 初始时间锚点 (如: 系统启动的当前时间)
     * @param parameter 模型的空间/轴定义图纸
     * @return 配置好的多维 TSShell 实例
     */
    public static TSShell createFromParameter(int featureId, Instant initialTime, Parameter parameter) {

        // 1. 初始化建造者
        TSShell.Builder builder = new TSShell.Builder(featureId);

        // 2. 获取原点坐标
        double xOrigin = parameter.getOriginPoint().getCoordinate().getX();
        double yOrigin = parameter.getOriginPoint().getCoordinate().getY();
        double zOrigin = Double.isNaN(parameter.getOriginPoint().getCoordinate().getZ())
                ? 0.0 : parameter.getOriginPoint().getCoordinate().getZ();

        // 3. 遍历图纸中的轴列表，指挥建造者进行组装
        List<Axis> axisList = parameter.getAxisList();
        if (axisList != null) {
            for (Axis axis : axisList) {
                if (axis instanceof TimeAxis) {
                    builder.time(initialTime, (TimeAxis) axis);
                } else if (axis instanceof SpaceAxisZ) {
                    builder.z(zOrigin, (SpaceAxisZ) axis);
                } else if (axis instanceof SpaceAxisY) {
                    builder.y(yOrigin, (SpaceAxisY) axis);
                } else if (axis instanceof SpaceAxisX) {
                    builder.x(xOrigin, (SpaceAxisX) axis);
                }
            }
        }

        // 4. 终极组装 (此处会自动触发 Builder 内部的维度排序逻辑)
        return builder.build();
    }
}
