package org.example.device;

import org.example.model.ChamberState;
import org.example.model.SensorType;

/** Raises temperature by 5C, never above 30C. Spec 3.3 device, added as one class (OCP). */
public class HeatingUnit implements Device {

    @Override
    public String name() {
        return "Heating Unit";
    }

    @Override
    public String apply(ChamberState state) {
        double temperature = state.adjust(SensorType.TEMPERATURE, v -> Math.min(30, v + 5));
        return "Temperature increased to " + temperature + "C";
    }
}
