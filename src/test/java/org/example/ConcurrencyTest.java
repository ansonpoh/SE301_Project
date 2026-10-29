package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import org.example.alert.Alert;
import org.example.alert.AlertStore;
import org.example.alert.AlertType;
import org.example.alert.Severity;
import org.example.control.Activation;
import org.example.metrics.ChamberActivityTracker;
import org.example.metrics.Resource;
import org.example.metrics.ResourceMeter;
import org.example.metrics.ResourceUsage;
import org.example.model.ChamberState;
import org.example.model.SensorType;
import org.junit.jupiter.api.RepeatedTest;

/**
 * Stress tests for the shared state that was racy in the legacy system. Each test hammers one
 * component from many threads and checks that no update was lost.
 */
class ConcurrencyTest {

    private static final int THREADS = 8;
    private static final int OPERATIONS = 5_000;

    @RepeatedTest(3)
    void resourceMeterLosesNoUpdates() throws InterruptedException {
        ResourceMeter meter = new ResourceMeter();

        TestFixtures.runConcurrently(THREADS, () -> {
            for (int i = 0; i < OPERATIONS; i++) {
                meter.record("CH1", ResourceUsage.water(2));
            }
        });

        assertEquals(2L * THREADS * OPERATIONS, meter.total(Resource.WATER));
    }

    @RepeatedTest(3)
    void alertStoreNeverLosesAlertsWhileArchiving() throws InterruptedException {
        AlertStore store = new AlertStore();
        Alert alert = new Alert("CH1", "Tomato", AlertType.TEMPERATURE, Severity.WARNING, "test", Instant.now());

        TestFixtures.runConcurrently(THREADS, () -> {
            for (int i = 0; i < OPERATIONS; i++) {
                store.add(alert);
                if (i % 100 == 0) {
                    store.archiveActive();
                }
            }
        });

        long expected = (long) THREADS * OPERATIONS;
        assertEquals(expected, store.totalGenerated());
        assertEquals(expected, store.activeAlerts().size() + store.archivedAlerts().size());
    }

    @RepeatedTest(3)
    void chamberStateAdjustmentsAreAtomic() throws InterruptedException {
        ChamberState state = ChamberState.withDefaults();
        state.record(SensorType.LIGHT, 0);

        TestFixtures.runConcurrently(THREADS, () -> {
            for (int i = 0; i < OPERATIONS; i++) {
                state.adjust(SensorType.LIGHT, 1, Double.MAX_VALUE);
            }
        });

        assertEquals((double) THREADS * OPERATIONS, state.get(SensorType.LIGHT));
    }

    @RepeatedTest(3)
    void activityTrackerCountsEveryEvent() throws InterruptedException {
        ChamberActivityTracker tracker = new ChamberActivityTracker();
        Alert alert = new Alert("CH1", "Tomato", AlertType.HUMIDITY, Severity.INFO, "alert", Instant.now());
        Activation activation = new Activation("CH1", "Water Pump", List.of());

        TestFixtures.runConcurrently(THREADS, () -> {
            for (int i = 0; i < OPERATIONS; i++) {
                tracker.onAlert(alert);
                tracker.onActivation(activation);
            }
        });

        assertEquals(THREADS * OPERATIONS, tracker.activityOf("CH1").alerts());
        assertEquals(THREADS * OPERATIONS, tracker.activityOf("CH1").actions());
    }
}
