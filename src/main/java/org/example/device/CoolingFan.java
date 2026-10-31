package org.example.device;

import org.example.model.ChamberState;
import org.example.model.SensorType;

/** Lowers temperature by 5C, never below 18C. */
public class CoolingFan implements Device {

    @Override
    public String name() {
        return "Cooling Fan";
    }

    @Override
    public String apply(ChamberState state) {
        double temperature = state.adjust(SensorType.TEMPERATURE, v -> Math.max(18, v - 5));
        return "Temperature reduced to " + temperature + "C";
    }
}
