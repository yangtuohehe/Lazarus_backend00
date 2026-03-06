package com.example.lazarus_backend00.domain.axis;

public class TimeAxis extends Axis {
    // ✅ 必须加上这个无参构造函数，专供 Jackson 反序列化 JSON 时使用！
    public TimeAxis() {
    }
    public TimeAxis(Double range, String rangeUnit,
                    Double resolution, String resolutionUnit) {

        if (range == null || range <= 0) {
            throw new IllegalArgumentException("time range must be positive");
        }
        if (resolution == null || resolution <= 0) {
            throw new IllegalArgumentException("time resolution must be positive");
        }

        this.setResolution(resolution);
        this.setUnit(resolutionUnit != null ? resolutionUnit : rangeUnit);
        this.setCount((int) Math.round(range / resolution));
    }
}