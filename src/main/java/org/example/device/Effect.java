package org.example.device;

import java.util.Objects;

import org.example.model.ChamberState;
import org.example.model.SensorType;

/**
 * Moves one environmental value by {@code delta}, never beyond {@code limit}. Immutable.
 */
public record Effect(SensorType sensor, double delta, double limit) {

    public Effect {
        Objects.requireNonNull(sensor, "sensor");
        if (delta == 0) {
            throw new IllegalArgumentException("Effect delta must not be zero");
        }
    }

    public String applyTo(ChamberState state) {
        double newValue = state.adjust(sensor, delta, limit);
        String direction = delta > 0 ? "increased" : "reduced";
        return sensor.label() + " " + direction + " to " + sensor.format(newValue);
    }
}
