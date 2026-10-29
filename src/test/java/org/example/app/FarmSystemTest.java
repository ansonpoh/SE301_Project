package org.example.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.example.TestFixtures;
import org.example.metrics.Resource;
import org.example.model.Chamber;
import org.example.model.ChamberState;
import org.example.model.SensorType;
import org.example.report.FarmSnapshot;
import org.example.rule.StandardRules;
import org.example.simulation.SensorSource;
import org.junit.jupiter.api.Test;

class FarmSystemTest {

    /** Every sensor reads a fixed value; only temperature is out of range (too hot for tomatoes). */
    private static final Map<SensorType, Double> HOT_CHAMBER = Map.of(
            SensorType.TEMPERATURE, 35.0,
            SensorType.HUMIDITY, 70.0,
            SensorType.SOIL_MOISTURE, 50.0,
            SensorType.LIGHT, 700.0,
            SensorType.NUTRIENT, 60.0);

    private static List<Chamber> tomatoChambers(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new Chamber("CH" + i, TestFixtures.tomato(), ChamberState.withDefaults()))
                .toList();
    }

    @Test
    void everyAlertTriggersItsDeviceAndEveryRequestIsProcessed() throws Exception {
        int chambers = 4;
        int cycles = 25;
        SensorSource hot = (chamber, type) -> HOT_CHAMBER.get(type);
        // A tiny queue forces producers to block, exercising the producer-consumer hand-off.
        SimulationSettings settings = new SimulationSettings(cycles, 1, List.of());
        FarmSystem farm = new FarmSystem(tomatoChambers(chambers), hot, StandardRules.create(), settings);
        AtomicInteger activations = new AtomicInteger();
        farm.onActivation(activation -> activations.incrementAndGet());

        assertTimeoutPreemptively(Duration.ofSeconds(10), farm::runMonitoring, "monitoring must not deadlock");

        int expected = chambers * cycles;
        FarmSnapshot snapshot = farm.snapshot();
        assertEquals(expected, snapshot.alertsGenerated());
        assertEquals(expected, activations.get());
        assertEquals(expected * 10L, snapshot.totalUsage(Resource.ENERGY), "cooling fan uses 10 kWh per run");
        assertEquals((long) expected * SensorType.values().length, snapshot.sensorReadings());
    }

    @Test
    void archivingMovesAllActiveAlerts() throws Exception {
        SensorSource hot = (chamber, type) -> HOT_CHAMBER.get(type);
        FarmSystem farm = new FarmSystem(tomatoChambers(2), hot, StandardRules.create(),
                new SimulationSettings(5, 5, List.of()));
        farm.runMonitoring();

        assertEquals(10, farm.archiveAlerts());

        FarmSnapshot snapshot = farm.snapshot();
        assertEquals(0, snapshot.activeAlerts());
        assertEquals(10, snapshot.archivedAlerts().size());
    }
}
