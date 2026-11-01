package org.example.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.model.ChamberState;
import org.example.model.SensorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        // a reading already below the floor is never raised, and the log does not claim a reduction
        assertEquals(15, applyTo(new VentilationSystem(), SensorType.TEMPERATURE, 15));
        state.set(SensorType.TEMPERATURE, 15);
        state.set(SensorType.HUMIDITY, 90);
        assertEquals("Temperature already 15.0C, humidity reduced to 85.0%", new VentilationSystem().apply(state));
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
        assertEquals("Cooling Fan", registry.get("Cooling Fan").name());
        assertThrows(IllegalArgumentException.class, () -> registry.get("Misting Nozzle"));
        assertEquals("Cooling Fan", registry.find("Cooling Fan").orElseThrow().name());
        assertTrue(registry.find("Misting Nozzle").isEmpty());
    }

    /** A second device with the same name would silently take over every crops.csv rule for it. */
    @Test
    void registryRejectsADuplicateName() {
        DeviceRegistry registry = new DeviceRegistry().register(new CoolingFan());
        assertThrows(IllegalStateException.class, () -> registry.register(new CoolingFan()));
    }

    /** A device with no declared effects could not be checked against crops.csv, so it is a broken build. */
    @Test
    void registryRejectsADeviceWithNoEffects() {
        Device silent = new Device() {
            public String name() {
                return "Silent";
            }

            public String apply(ChamberState state) {
                return "nothing";
            }

            public Set<Effect> effects() {
                return Set.of();
            }
        };
        assertThrows(IllegalStateException.class, () -> new DeviceRegistry().register(silent));
    }

    /** Every spec 3.3 device (plus the legacy humidity pair) is found through META-INF/services. */
    @Test
    void discoverFindsEveryListedDevice() {
        DeviceRegistry registry = DeviceRegistry.discover();
        for (String name : new String[] {"Cooling Fan", "Water Pump", "Nutrient Injector", "Humidifier",
                "Dehumidifier", "Grow Light", "Heating Unit", "Ventilation System", "Shade Controller"}) {
            assertEquals(name, registry.get(name).name());
        }
    }

    /**
     * Each device's declared effects are what apply() really does: from mid-range
     * readings, every declared reading moves the declared way and no other moves.
     * The crops.csv check and the nutrient report both rely on this.
     */
    @Test
    void declaredEffectsMatchWhatEachDeviceDoes() {
        Map<SensorType, Double> mid = Map.of(SensorType.TEMPERATURE, 24.0, SensorType.HUMIDITY, 70.0,
                SensorType.SOIL, 50.0, SensorType.LIGHT, 500.0, SensorType.NUTRIENT, 50.0);
        DeviceRegistry registry = DeviceRegistry.discover();
        for (Device device : List.of(new CoolingFan(), new WaterPump(), new NutrientInjector(), new Humidifier(),
                new Dehumidifier(), new GrowLight(), new HeatingUnit(), new VentilationSystem(),
                new ShadeController())) {
            assertFalse(device.effects().isEmpty(), device.name());
            ChamberState state = new ChamberState();
            mid.forEach(state::set);
            device.apply(state);
            for (SensorType sensor : SensorType.values()) {
                double before = mid.get(sensor);
                double after = state.get(sensor);
                String what = device.name() + " " + sensor;
                if (device.effects().contains(Effect.raises(sensor))) {
                    assertTrue(after > before, what);
                } else if (device.effects().contains(Effect.lowers(sensor))) {
                    assertTrue(after < before, what);
                } else {
                    assertEquals(before, after, what);
                }
            }
        }
        assertEquals(List.of("Nutrient Injector"), registry.namesWith(Effect.raises(SensorType.NUTRIENT)));
        assertEquals(List.of("Cooling Fan", "Ventilation System"), registry.namesWith(Effect.lowers(SensorType.TEMPERATURE)));
    }

    /**
     * A typo in the services file is a broken build: IllegalStateException (exit
     * status 3 in SVFRMS), not a ServiceConfigurationError escaping main().
     */
    @Test
    void discoverReportsABadServicesLineAsABrokenBuild(@TempDir Path dir) throws Exception {
        Path services = dir.resolve("META-INF/services/" + Device.class.getName());
        Files.createDirectories(services.getParent());
        Files.writeString(services, "org.example.device.NoSuchDevice\n");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {dir.toUri().toURL()},
                Device.class.getClassLoader())) {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> DeviceRegistry.discover(loader));
            assertTrue(e.getMessage().contains("NoSuchDevice"), e.getMessage());
        }
    }
}
