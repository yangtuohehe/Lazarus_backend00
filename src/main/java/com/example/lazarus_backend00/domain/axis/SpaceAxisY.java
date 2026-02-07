package com.example.lazarus_backend00.domain.axis;

public class SpaceAxisY extends Axis {

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