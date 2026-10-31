package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;

import org.example.alert.Alert;
import org.example.alert.AlertStore;
import org.example.alert.Severity;
import org.example.model.ChamberState;
import org.example.model.SensorType;
import org.example.report.ChamberSummaryTracker;
import org.example.report.ResourceMeter;
import org.example.report.SensorLog;
import org.example.report.SeverityLog;
import org.junit.jupiter.api.RepeatedTest;

/**
 * Hammers each piece of shared state that was racy in the legacy code (C1-C5)
 * from many threads and checks that no update is lost.
 */
class ConcurrencyTest {
    private static final int THREADS = 8;
    private static final int OPERATIONS = 5_000;
    private static final int TOTAL = THREADS * OPERATIONS;

    /** C3: legacy "totalWaterUsage += n" lost updates. */
    @RepeatedTest(3)
    void resourceMeterLosesNoUpdates() throws InterruptedException {
        ResourceMeter meter = new ResourceMeter();

        TestFixtures.runConcurrently(THREADS, () -> {
            for (int i = 0; i < OPERATIONS; i++) {
                meter.addWater(2);
                meter.addEnergy(1);
            }
        });

        assertEquals(2 * TOTAL, meter.water());
        assertEquals(TOTAL, meter.energy());
    }

    /** C2, C7: legacy alerts.add() was unsynchronized and archiving locked only one side. */
    @RepeatedTest(3)
    void alertStoreLosesNoAlertsWhileArchiving() throws InterruptedException {
        AlertStore store = new AlertStore();
        Alert alert = new Alert("CH1", "Tomato", "CH1 Tomato High Temp", Severity.CRITICAL);

        TestFixtures.runConcurrently(THREADS, () -> {
            for (int i = 0; i < OPERATIONS; i++) {
                store.onAlert(alert);
                if (i % 100 == 0) {
                    store.archiveActive();
                }
            }
        });

        assertEquals(TOTAL, store.generatedCount());
        assertEquals(TOTAL, store.activeCount() + store.archived().size());
    }

    /** C4: legacy device code did "temperature - 5" on a field another thread was writing. */
    @RepeatedTest(3)
    void chamberStateAdjustIsAtomic() throws InterruptedException {
        ChamberState state = new ChamberState();
        state.set(SensorType.LIGHT, 0);

        TestFixtures.runConcurrently(THREADS, () -> {
            for (int i = 0; i < OPERATIONS; i++) {
                state.adjust(SensorType.LIGHT, v -> v + 1);
            }
        });

        assertEquals(TOTAL, state.get(SensorType.LIGHT));
    }

    /**
     * C1, C5: legacy containsKey/put/get on a HashMap could lose a chamber's
     * summary or crash with an NPE. Every thread creates the same chambers at once.
     */
    @RepeatedTest(3)
    void summaryTrackerKeepsOneSummaryPerChamber() throws InterruptedException {
        ChamberSummaryTracker tracker = new ChamberSummaryTracker();
        String[] chambers = {"CH1", "CH2", "CH3", "CH4"};

        TestFixtures.runConcurrently(THREADS, () -> {
            for (int i = 0; i < OPERATIONS; i++) {
                String chamber = chambers[i % chambers.length];
                tracker.onAlert(new Alert(chamber, "Tomato", chamber + " alert", Severity.WARNING));
                tracker.recordAction(chamber, "Cooling Fan Activated");
            }
        });

        assertEquals(chambers.length, tracker.sortedSummaries().size());
        int perChamber = TOTAL / chambers.length;
        tracker.sortedSummaries().values().forEach(s -> {
            assertEquals(perChamber, s.alerts());
            assertEquals(perChamber, s.mitigationActions());
        });
    }

    /** C2: legacy sensorLogs was an unsynchronized ArrayList. */
    @RepeatedTest(3)
    void sensorLogLosesNoEntries() throws InterruptedException {
        SensorLog log = new SensorLog(Clock.systemUTC());

        TestFixtures.runConcurrently(THREADS, () -> {
            for (int i = 0; i < OPERATIONS; i++) {
                log.record("reading");
            }
        });

        assertEquals(TOTAL, log.size());
    }

    /** The severity log is one more listener called from every chamber thread. */
    @RepeatedTest(3)
    void severityLogLosesNoAlerts() throws InterruptedException {
        SeverityLog log = new SeverityLog();
        Alert critical = new Alert("CH1", "Tomato", "CH1 Tomato High Temp", Severity.CRITICAL);
        Alert info = new Alert("CH1", "Tomato", "CH1 Low Light Level", Severity.INFO);

        TestFixtures.runConcurrently(THREADS, () -> {
            for (int i = 0; i < OPERATIONS; i++) {
                log.onAlert(i % 2 == 0 ? critical : info);
            }
        });

        assertEquals(TOTAL / 2, log.count(Severity.CRITICAL));
        assertEquals(TOTAL / 2, log.count(Severity.INFO));
        assertEquals(TOTAL, log.entries().size());
    }
}
