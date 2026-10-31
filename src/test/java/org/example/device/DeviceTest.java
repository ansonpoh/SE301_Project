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
        assertThrows(IllegalArgumentException.class, () -> registry.get("Heating Unit"));
    }
}
