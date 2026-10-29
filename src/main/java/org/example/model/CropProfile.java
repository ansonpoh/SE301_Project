package org.example.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Acceptable growing conditions for one crop. Immutable.
 * Every {@link SensorType} must have a range, so rules never need crop-specific branches.
 */
public record CropProfile(String name, Map<SensorType, Range> ranges) {

    public CropProfile {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Crop name must not be blank");
        }
        Objects.requireNonNull(ranges, "ranges");
        for (SensorType type : SensorType.values()) {
            if (!ranges.containsKey(type)) {
                throw new IllegalArgumentException("Crop " + name + " has no range for " + type.label());
            }
        }
        ranges = Collections.unmodifiableMap(new EnumMap<>(ranges));
    }

    public Range range(SensorType type) {
        return ranges.get(type);
    }
}
