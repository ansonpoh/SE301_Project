package org.example.device;

import org.example.model.ChamberState;
import org.example.model.SensorType;

/** Raises nutrient level by 15%, never above 100%. */
public class NutrientInjector implements Device {

    @Override
    public String name() {
        return "Nutrient Injector";
    }

    @Override
    public String apply(ChamberState state) {
        double nutrient = state.adjust(SensorType.NUTRIENT, v -> Math.min(100, v + 15));
        return "Nutrient level increased to " + nutrient + "%";
    }
}
