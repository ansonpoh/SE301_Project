package org.example.device;

import org.example.model.ChamberState;
import org.example.model.SensorType;

/** Lowers humidity by 10%, never below 30%. */
public class Dehumidifier implements Device {

    @Override
    public String name() {
        return "Dehumidifier";
    }

    @Override
    public String apply(ChamberState state) {
        double humidity = state.adjust(SensorType.HUMIDITY, v -> Math.max(30, v - 10));
        return "Humidity reduced to " + humidity + "%";
    }
}
