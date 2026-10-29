package org.example.model;

import java.time.Instant;
import java.util.Objects;

/** One measurement taken by a chamber sensor. Immutable. */
public record SensorReading(String chamberId, SensorType type, double value, Instant timestamp) {

    public SensorReading {
        Objects.requireNonNull(chamberId, "chamberId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    public boolean isValid() {
        return type.isPlausible(value);
    }
}
