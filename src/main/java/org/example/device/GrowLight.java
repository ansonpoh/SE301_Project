package org.example.device;

import org.example.model.ChamberState;
import org.example.model.SensorType;

/** Raises light level by 150 lux, never above 1000 lux. */
public class GrowLight implements Device {

    @Override
    public String name() {
        return "Grow Light";
    }

    @Override
    public String apply(ChamberState state) {
        double light = state.adjust(SensorType.LIGHT, v -> Math.min(1000, v + 150));
        return "Light level increased to " + light + " lux";
    }
}
