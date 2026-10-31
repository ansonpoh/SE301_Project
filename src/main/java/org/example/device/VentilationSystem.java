package org.example.device;

import java.util.Map;

import org.example.model.ChamberState;
import org.example.model.SensorType;

/**
 * Lowers temperature by 2C (never below 18C) and humidity by 5% (never below
 * 30%). Spec 3.3 device, added as one class (OCP). Like the Heating Unit, it
 * never moves a reading the wrong way if a newer reading is already past its limit,
 * and says so instead of logging a reduction. Both readings change in one atomic
 * step, so its log line is one real state.
 */
public class VentilationSystem implements Device {

    @Override
    public String name() {
        return "Ventilation System";
    }

    @Override
    public String apply(ChamberState state) {
        Map<SensorType, ChamberState.Change> changes = state.changeAll(Map.of(
                SensorType.TEMPERATURE, v -> Math.min(v, Math.max(18, v - 2)),
                SensorType.HUMIDITY, v -> Math.min(v, Math.max(30, v - 5))));
        return describe("Temperature", changes.get(SensorType.TEMPERATURE), "C") + ", "
                + describe("humidity", changes.get(SensorType.HUMIDITY), "%");
    }

    /** e.g. "Temperature reduced to 28.0C" or "humidity already 30.0%". */
    private static String describe(String reading, ChamberState.Change change, String unit) {
        return reading + (change.after() < change.before() ? " reduced to " : " already ") + change.after() + unit;
    }
}
