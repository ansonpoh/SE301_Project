package org.example.device;

import org.example.model.ChamberState;
import org.example.model.SensorType;

/**
 * Raises temperature by 5C, never above 30C. Spec 3.3 device, added as one class (OCP).
 *
 * The request runs on the control thread, after the chamber may already have
 * stored a newer, hotter reading, so a heater must never lower the temperature.
 */
public class HeatingUnit implements Device {
    private static final double STEP = 5;
    private static final double MAX = 30;

    @Override
    public String name() {
        return "Heating Unit";
    }

    @Override
    public String apply(ChamberState state) {
        ChamberState.Change change = state.change(SensorType.TEMPERATURE, v -> Math.max(v, Math.min(MAX, v + STEP)));
        return change.after() > change.before()
                ? "Temperature increased to " + change.after() + "C"
                : "Temperature already " + change.after() + "C, no heating needed";
    }
}
