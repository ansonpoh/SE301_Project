package org.example.device;

import org.example.model.ChamberState;
import org.example.model.SensorType;

/**
 * Lowers temperature by 2C (never below 18C) and humidity by 5% (never below
 * 30%). Spec 3.3 device, added as one class (OCP). Like the Heating Unit, it
 * never moves a reading the wrong way if a newer reading is already past its limit.
 */
public class VentilationSystem implements Device {

    @Override
    public String name() {
        return "Ventilation System";
    }

    @Override
    public String apply(ChamberState state) {
        double temperature = state.adjust(SensorType.TEMPERATURE, v -> Math.min(v, Math.max(18, v - 2)));
        double humidity = state.adjust(SensorType.HUMIDITY, v -> Math.min(v, Math.max(30, v - 5)));
        return "Temperature reduced to " + temperature + "C, humidity reduced to " + humidity + "%";
    }
}
