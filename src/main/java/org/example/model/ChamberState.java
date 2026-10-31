package org.example.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * Current readings of one chamber.
 *
 * Written by the chamber's monitor thread (new readings) and by the control
 * thread (device adjustments), so every method is synchronized on this object.
 * adjust() makes a device's read-modify-write atomic, which the legacy
 * activateControl() did not.
 */
public class ChamberState {
    private final Map<SensorType, Double> values = new EnumMap<>(SensorType.class);

    public ChamberState() {
        for (SensorType type : SensorType.values()) {
            values.put(type, type.initialValue());
        }
    }

    public synchronized double get(SensorType type) {
        return values.get(type);
    }

    public synchronized void set(SensorType type, double value) {
        values.put(type, value);
    }

    /** Applies a change to one reading atomically and returns the new value. */
    public synchronized double adjust(SensorType type, DoubleUnaryOperator change) {
        double updated = change.applyAsDouble(values.get(type));
        values.put(type, updated);
        return updated;
    }
}
