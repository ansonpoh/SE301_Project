package org.example.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Latest environmental values of one chamber.
 * <p>
 * Thread-safe: it is written by the chamber's monitor (new readings) and by the control worker
 * (device effects) at the same time. Every access goes through this object's own monitor and no
 * other lock is ever taken while holding it, so it cannot take part in a deadlock.
 */
public final class ChamberState {

    private final Map<SensorType, Double> values = new EnumMap<>(SensorType.class);

    public ChamberState(Map<SensorType, Double> initialValues) {
        for (SensorType type : SensorType.values()) {
            Double value = initialValues.get(type);
            if (value == null) {
                throw new IllegalArgumentException("No initial value for " + type.label());
            }
            values.put(type, value);
        }
    }

    /** Starting conditions used by the legacy system for every chamber. */
    public static ChamberState withDefaults() {
        return new ChamberState(Map.of(
                SensorType.TEMPERATURE, 24.0,
                SensorType.HUMIDITY, 70.0,
                SensorType.SOIL_MOISTURE, 60.0,
                SensorType.LIGHT, 600.0,
                SensorType.NUTRIENT, 70.0));
    }

    public synchronized void record(SensorType type, double value) {
        values.put(type, value);
    }

    public synchronized double get(SensorType type) {
        return values.get(type);
    }

    /**
     * Atomically moves a value by {@code delta}, never past {@code limit}
     * (an upper limit when delta is positive, a lower limit otherwise).
     *
     * @return the new value
     */
    public synchronized double adjust(SensorType type, double delta, double limit) {
        double moved = values.get(type) + delta;
        double adjusted = delta >= 0 ? Math.min(limit, moved) : Math.max(limit, moved);
        values.put(type, adjusted);
        return adjusted;
    }

    /** Consistent copy of all values, safe to read without further locking. */
    public synchronized Map<SensorType, Double> snapshot() {
        return Collections.unmodifiableMap(new EnumMap<>(values));
    }
}
