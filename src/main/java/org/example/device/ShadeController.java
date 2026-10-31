package org.example.device;

import org.example.model.ChamberState;
import org.example.model.SensorType;

/** Lowers light level by 200 lux, never below 0 lux. Spec 3.3 device, added as one class (OCP). */
public class ShadeController implements Device {

    @Override
    public String name() {
        return "Shade Controller";
    }

    @Override
    public String apply(ChamberState state) {
        double light = state.adjust(SensorType.LIGHT, v -> Math.max(0, v - 200));
        return "Light level reduced to " + light + " lux";
    }
}
