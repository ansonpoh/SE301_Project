package org.example.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.example.model.ChamberState;
import org.example.model.SensorType;
import org.junit.jupiter.api.Test;

/** Each device's delta and limit, copied from the legacy activateControl(). */
class DeviceTest {

    private static double applyTo(Device device, SensorType sensor, double start) {
        ChamberState state = new ChamberState();
        state.set(sensor, start);
        device.apply(state);
        return state.get(sensor);
    }

    @Test
    void coolingFanLowersBy5ButNotBelow18() {
        assertEquals(25, applyTo(new CoolingFan(), SensorType.TEMPERATURE, 30));
        assertEquals(18, applyTo(new CoolingFan(), SensorType.TEMPERATURE, 20));
    }

    @Test
    void waterPumpRaisesBy20ButNotAbove100() {
        assertEquals(60, applyTo(new WaterPump(), SensorType.SOIL, 40));
        assertEquals(100, applyTo(new WaterPump(), SensorType.SOIL, 90));
    }

    @Test
    void nutrientInjectorRaisesBy15ButNotAbove100() {
        assertEquals(45, applyTo(new NutrientInjector(), SensorType.NUTRIENT, 30));
        assertEquals(100, applyTo(new NutrientInjector(), SensorType.NUTRIENT, 95));
    }

    @Test
    void humidifierAndDehumidifierStayWithinLimits() {
        assertEquals(60, applyTo(new Humidifier(), SensorType.HUMIDITY, 50));
        assertEquals(100, applyTo(new Humidifier(), SensorType.HUMIDITY, 95));
        assertEquals(80, applyTo(new Dehumidifier(), SensorType.HUMIDITY, 90));
        assertEquals(30, applyTo(new Dehumidifier(), SensorType.HUMIDITY, 35));
    }

    @Test
    void growLightRaisesBy150ButNotAbove1000() {
        assertEquals(550, applyTo(new GrowLight(), SensorType.LIGHT, 400));
        assertEquals(1000, applyTo(new GrowLight(), SensorType.LIGHT, 950));
    }

    @Test
    void heatingUnitRaisesBy5ButNotAbove30() {
        assertEquals(20, applyTo(new HeatingUnit(), SensorType.TEMPERATURE, 15));
        assertEquals(30, applyTo(new HeatingUnit(), SensorType.TEMPERATURE, 28));
    }

    /**
     * The request runs after the chamber may have stored a newer, hotter
     * reading: the heater must leave it alone and say so, not "increase" it to 30.
     */
    @Test
    void heatingUnitNeverLowersANewerHotterReading() {
        assertEquals(36, applyTo(new HeatingUnit(), SensorType.TEMPERATURE, 36));
        ChamberState state = new ChamberState();
        state.set(SensorType.TEMPERATURE, 36);
        assertEquals("Temperature already 36.0C, no heating needed", new HeatingUnit().apply(state));
        state.set(SensorType.TEMPERATURE, 20);
        assertEquals("Temperature increased to 25.0C", new HeatingUnit().apply(state));
    }

    @Test
    void shadeControllerLowersLightBy200ButNotBelow0() {
        assertEquals(900, applyTo(new ShadeController(), SensorType.LIGHT, 1100));
        assertEquals(0, applyTo(new ShadeController(), SensorType.LIGHT, 150));
    }

    @Test
    void ventilationLowersTemperatureAndHumidityWithinLimits() {
        ChamberState state = new ChamberState();
        state.set(SensorType.TEMPERATURE, 30);
        state.set(SensorType.HUMIDITY, 90);
        assertEquals("Temperature reduced to 28.0C, humidity reduced to 85.0%", new VentilationSystem().apply(state));

        assertEquals(18, applyTo(new VentilationSystem(), SensorType.TEMPERATURE, 19));
        assertEquals(30, applyTo(new VentilationSystem(), SensorType.HUMIDITY, 32));
        // a reading already below the floor is never raised
        assertEquals(15, applyTo(new VentilationSystem(), SensorType.TEMPERATURE, 15));
    }

    @Test
    void logTextMatchesTheLegacyFormat() {
        ChamberState state = new ChamberState();
        state.set(SensorType.TEMPERATURE, 31);
        assertEquals("Temperature reduced to 26.0C", new CoolingFan().apply(state));
    }

    @Test
    void registryFindsDevicesByName() {
        DeviceRegistry registry = new DeviceRegistry().register(new CoolingFan());
        assertTrue(registry.contains("Cooling Fan"));
        assertEquals("Cooling Fan", registry.get("Cooling Fan").name());
        assertThrows(IllegalArgumentException.class, () -> registry.get("Misting Nozzle"));
    }
}
