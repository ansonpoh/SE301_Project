package org.example.sensor;

import java.util.EnumMap;
import java.util.Map;

import org.example.model.SensorType;

/**
 * The range of readings a working sensor can produce, per sensor type, loaded
 * from sensors.csv like the crop thresholds. A reading outside it (NaN, a
 * negative humidity, ...) raises a Sensor Failure alert (spec 3.4), so a
 * different sensor model is a data change, not a Java change.
 * Read-only after loading, so it is safe to share between threads.
 */
public final class SensorRanges {
    public record Range(double min, double max) {
        public Range {
            if (!(min <= max)) {
                throw new IllegalArgumentException("min " + min + " is above max " + max);
            }
        }

        /** False for NaN, since every comparison with NaN is false. */
        public boolean contains(double value) {
            return value >= min && value <= max;
        }
    }

    private final Map<SensorType, Range> ranges;

    /** @throws IllegalArgumentException if a sensor type has no range */
    public SensorRanges(Map<SensorType, Range> ranges) {
        for (SensorType sensor : SensorType.values()) {
            if (!ranges.containsKey(sensor)) {
                throw new IllegalArgumentException("no valid range for sensor " + sensor);
            }
        }
        this.ranges = new EnumMap<>(ranges);
    }

    public boolean isValid(SensorType sensor, double value) {
        return ranges.get(sensor).contains(value);
    }
}
