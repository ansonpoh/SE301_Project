package org.example.device;

import java.util.Set;

import org.example.model.ChamberState;
import org.example.model.SensorType;

/** Raises soil moisture by 20%, never above 100%. */
public class WaterPump implements Device {

    @Override
    public String name() {
        return "Water Pump";
    }

    @Override
    public Set<Effect> effects() {
        return Set.of(Effect.raises(SensorType.SOIL));
    }

    @Override
    public String apply(ChamberState state) {
        double soil = state.adjust(SensorType.SOIL, v -> Math.min(100, v + 20));
        return "Soil moisture increased to " + soil + "%";
    }
}
