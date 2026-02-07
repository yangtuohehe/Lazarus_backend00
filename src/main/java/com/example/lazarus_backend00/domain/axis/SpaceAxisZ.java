package com.example.lazarus_backend00.domain.axis;

public class SpaceAxisZ extends Axis {

    public SpaceAxisZ(Double range, String rangeUnit,
                      Double resolution, String resolutionUnit) {

        if (range == null || range <= 0) {
            throw new IllegalArgumentException("Z range must be positive");
        }
        if (resolution == null || resolution <= 0) {
            throw new IllegalArgumentException("Z resolution must be positive");
        }

        this.setResolution(resolution);
        this.setUnit(resolutionUnit != null ? resolutionUnit : rangeUnit);
        this.setCount((int) Math.round(range / resolution));
    }
}