package com.example.lazarus_backend00.domain.axis;

public class SpaceAxisX extends Axis {

    public SpaceAxisX(Double range, String rangeUnit,
                      Double resolution, String resolutionUnit) {

        // 1. 参数校验
        if (range == null || range <= 0) {
            throw new IllegalArgumentException("X range must be positive");
        }
        if (resolution == null || resolution <= 0) {
            throw new IllegalArgumentException("X resolution must be positive");
        }

        // 2. 设置父类属性
        this.setResolution(resolution);

        // 统一单位：通常优先使用分辨率的单位，或者假定 range 和 resolution 单位一致
        this.setUnit(resolutionUnit != null ? resolutionUnit : rangeUnit);

        // 3. 计算 count (行数 = 总范围 / 分辨率)
        // 使用 Math.round 四舍五入，或者 Math.ceil 向上取整，取决于具体业务需求
        // 这里假设 range 和 resolution 单位已经对齐
        this.setCount((int) Math.round(range / resolution));
    }
}
