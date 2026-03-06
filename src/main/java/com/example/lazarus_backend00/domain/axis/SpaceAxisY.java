package com.example.lazarus_backend00.domain.axis;

public class SpaceAxisY extends Axis {
    // ✅ 必须加上这个无参构造函数，专供 Jackson 反序列化 JSON 时使用！
    public SpaceAxisY() {
    }
    public SpaceAxisY(Double range, String rangeUnit,
                      Double resolution, String resolutionUnit) {

        if (range == null || range <= 0) {
            throw new IllegalArgumentException("Y range must be positive");
        }
        if (resolution == null || resolution <= 0) {
            throw new IllegalArgumentException("Y resolution must be positive");
        }

        this.setResolution(resolution);
        this.setUnit(resolutionUnit != null ? resolutionUnit : rangeUnit);
        this.setCount((int) Math.round(range / resolution));
    }
}