package org.example.device;

import org.example.model.ChamberState;
import org.example.model.SensorType;

/** Raises humidity by 10%, never above 100%. */
public class Humidifier implements Device {

    @Override
    public String name() {
        return "Humidifier";
    }

    @Override
    public String apply(ChamberState state) {
        double humidity = state.adjust(SensorType.HUMIDITY, v -> Math.min(100, v + 10));
        return "Humidity increased to " + humidity + "%";
    }
}
