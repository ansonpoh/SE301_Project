package org.example.device;

import java.util.List;

import org.example.metrics.ResourceUsage;
import org.example.model.SensorType;

/**
 * The devices installed in every chamber (spec section 3.3, plus the humidity devices the
 * legacy system already used). Devices are immutable, so sharing these constants is safe.
 */
public final class StandardDevices {

    public static final Device WATER_PUMP = new EnvironmentalDevice("Water Pump",
            List.of(new Effect(SensorType.SOIL_MOISTURE, 20, 100)),
            List.of(ResourceUsage.water(50), ResourceUsage.energy(2)));

    public static final Device COOLING_FAN = new EnvironmentalDevice("Cooling Fan",
            List.of(new Effect(SensorType.TEMPERATURE, -5, 18)),
            List.of(ResourceUsage.energy(10)));

    public static final Device HEATING_UNIT = new EnvironmentalDevice("Heating Unit",
            List.of(new Effect(SensorType.TEMPERATURE, 5, 35)),
            List.of(ResourceUsage.energy(15)));

    public static final Device GROW_LIGHT = new EnvironmentalDevice("Grow Light",
            List.of(new Effect(SensorType.LIGHT, 150, 1000)),
            List.of(ResourceUsage.energy(8)));

    public static final Device NUTRIENT_INJECTOR = new EnvironmentalDevice("Nutrient Injector",
            List.of(new Effect(SensorType.NUTRIENT, 15, 100)),
            List.of(ResourceUsage.nutrient(150), ResourceUsage.energy(1)));

    public static final Device VENTILATION_SYSTEM = new EnvironmentalDevice("Ventilation System",
            List.of(new Effect(SensorType.HUMIDITY, -5, 30), new Effect(SensorType.TEMPERATURE, -2, 18)),
            List.of(ResourceUsage.energy(3)));

    public static final Device SHADE_CONTROLLER = new EnvironmentalDevice("Shade Controller",
            List.of(new Effect(SensorType.LIGHT, -200, 200)),
            List.of(ResourceUsage.energy(1)));

    public static final Device HUMIDIFIER = new EnvironmentalDevice("Humidifier",
            List.of(new Effect(SensorType.HUMIDITY, 10, 100)),
            List.of(ResourceUsage.water(20), ResourceUsage.energy(2)));

    public static final Device DEHUMIDIFIER = new EnvironmentalDevice("Dehumidifier",
            List.of(new Effect(SensorType.HUMIDITY, -10, 30)),
            List.of(ResourceUsage.energy(5)));

    private StandardDevices() {
    }
}
