package org.example.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.example.SVFRMS;
import org.example.TestFixtures;
import org.example.alert.AlertPublisher;
import org.example.alert.AlertStore;
import org.example.alert.Severity;
import org.example.crop.CropRegistry;
import org.example.crop.ThresholdRule;
import org.example.device.ControlService;
import org.example.device.ControlSink;
import org.example.device.CoolingFan;
import org.example.device.Device;
import org.example.device.Effect;
import org.example.model.ChamberState;
import org.example.model.SensorType;
import org.example.report.ChamberSummaryTracker;
import org.example.report.DailyReport;
import org.example.report.NutrientUsageReport;
import org.example.report.ResourceMeter;
import org.example.report.ResourceUsageReport;
import org.example.report.SensorLog;
import org.example.report.SeverityLog;
import org.example.report.WeeklyCropReport;
import org.example.sensor.ChamberMonitor;
import org.example.sensor.ReadingLog;
import org.example.sensor.SensorProcessor;
import org.example.sensor.SensorSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the whole system with the real wiring and every chamber running
 * concurrently. Every chamber reads 35C, which is above the high-temperature
 * limit of every scheduled crop and nothing else is out of range, so the exact
 * totals are known.
 */
class FarmSimulationTest {
    private static final int CYCLES = 20;

    @TempDir
    Path dir;

    /** Built-in chambers.csv: the four legacy chambers, so the legacy totals. */
    @Test
    void concurrentRunProducesExactTotals() throws Exception {
        // +10 kWh per Tomato and Strawberry High Temp alert
        assertExactTotals(null, List.of("CH1", "CH2", "CH3", "CH4"), 2 * 10);
    }

    /** Spec 4.5: the built-in demo/chambers.csv adds Kale, Bok Choy and Bell Pepper with no code change. */
    @Test
    void demoConfigRunsTheNewCrops() throws Exception {
        // +10 kWh per Tomato and Strawberry, +8 kWh per Kale and Bok Choy High Temp alert
        assertExactTotals(Path.of(System.getProperty("basedir", ".")).resolve("src/main/resources/demo"), List.of("CH1", "CH2", "CH3", "CH4", "CH5", "CH6", "CH7"), 2 * 10 + 2 * 8);
    }

    /**
     * A daily report that cannot be written fails the run (exit status 1), it is
     * not just a console line. Only daily_report.txt is blocked (a folder is in
     * its place); the other outputs save fine, so only DailyReport can fail the run.
     */
    @Test
    void unsavedReportFailsTheRun() throws Exception {
        Path reportFile = Files.createDirectories(dir.resolve("archive").resolve("daily_report.txt"));
        PrintStream silent = new PrintStream(new ByteArrayOutputStream());

        boolean ok = assertTimeoutPreemptively(Duration.ofSeconds(20), () ->
                SVFRMS.createSimulation(TestFixtures.fixed(TestFixtures.HOT_CHAMBER), 1, null, silent, silent,
                        Clock.systemUTC()).run(reportFile));

        assertFalse(ok);
        assertTrue(Files.isRegularFile(reportFile.resolveSibling(SensorLog.FILE_NAME)));
        assertTrue(Files.isRegularFile(reportFile.resolveSibling(SeverityLog.FILE_NAME)));
    }

    /** An interrupted chamber stops at its next cycle instead of raising alerts after the report. */
    @Test
    void interruptedChamberStopsReading() {
        AtomicInteger reads = new AtomicInteger();
        SensorSource sensors = type -> {
            reads.incrementAndGet();
            return TestFixtures.HOT_CHAMBER.get(type);
        };
        PrintStream silent = new PrintStream(new ByteArrayOutputStream());
        CropRegistry crops = new CropRegistry(Map.of(), List.of());
        SensorProcessor processor = new SensorProcessor(crops, new AlertPublisher(silent), new ResourceMeter(),
                request -> { }, message -> { }, new FarmConfigLoader(null, null).loadSensorRanges("sensors.csv"));

        ChamberMonitor monitor = new ChamberMonitor("CH1", "Tomato", sensors, processor, new ResourceMeter());
        Thread.currentThread().interrupt();
        try {
            monitor.run();
        } finally {
            Thread.interrupted();
        }

        assertEquals(0, reads.get());
        assertEquals(0, monitor.cyclesDone(), "FarmSimulation reports it as a partial day");
    }

    /**
     * A chamber interrupted while waiting to queue a device request stops like
     * one interrupted between readings: a partial day, not a chamber failure.
     */
    @Test
    void chamberInterruptedWhileQueueingStopsCleanly() {
        AtomicInteger requests = new AtomicInteger();
        ControlSink interrupted = request -> {
            requests.incrementAndGet();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while queueing", new InterruptedException());
        };
        ChamberMonitor monitor = hotTomatoMonitor(interrupted);
        try {
            monitor.run();   // must not throw
            assertTrue(Thread.currentThread().isInterrupted(), "interrupt flag must be kept");
        } finally {
            Thread.interrupted();
        }

        assertEquals(1, requests.get(), "stops at the interrupted request");
        assertEquals(0, monitor.cyclesDone(), "FarmSimulation reports it as a partial day");
    }

    /** Any other refusal (the control thread died) is still a chamber failure (issue C11). */
    @Test
    void deadControlThreadStillFailsTheChamber() {
        ControlSink dead = request -> {
            throw new IllegalStateException("Control thread has stopped");
        };
        assertThrows(IllegalStateException.class, () -> hotTomatoMonitor(dead).run());
    }

    /** CH1 Tomato at 35C, with one rule that queues a Cooling Fan request on every high reading. */
    private static ChamberMonitor hotTomatoMonitor(ControlSink control) {
        PrintStream silent = new PrintStream(new ByteArrayOutputStream());
        CropRegistry crops = new CropRegistry(Map.of(), List.of(new ThresholdRule(SensorType.TEMPERATURE,
                ThresholdRule.Direction.HIGH, 30, new CoolingFan(), 0, 0, Severity.CRITICAL, "{crop} High Temp")));
        SensorProcessor processor = new SensorProcessor(crops, new AlertPublisher(silent), new ResourceMeter(),
                control, message -> { }, new FarmConfigLoader(null, null).loadSensorRanges("sensors.csv"));
        return new ChamberMonitor("CH1", "Tomato", TestFixtures.fixed(TestFixtures.HOT_CHAMBER), processor,
                new ResourceMeter());
    }

    private void assertExactTotals(Path configDir, List<String> chambers, int extraEnergyPerCycle) throws Exception {
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        PrintStream silent = new PrintStream(new ByteArrayOutputStream());
        Path reportFile = dir.resolve("archive").resolve("daily_report.txt");

        boolean ok = assertTimeoutPreemptively(Duration.ofSeconds(20), () ->
                SVFRMS.createSimulation(TestFixtures.fixed(TestFixtures.HOT_CHAMBER), Integer.MAX_VALUE, configDir,
                        new PrintStream(console, true, StandardCharsets.UTF_8), silent, Clock.systemUTC())
                        .run(reportFile));

        assertTrue(ok, "every chamber should finish without error");
        int alerts = chambers.size() * CYCLES;
        int energy = alerts + CYCLES * extraEnergyPerCycle;   // +1 kWh per cycle
        int water = alerts * 2;

        List<String> report = Files.readAllLines(reportFile);
        assertTrue(report.contains("Generated Alerts: " + alerts));
        assertTrue(report.contains("Active Alerts: 0"));
        assertTrue(report.contains("Water Usage: " + water + " L"));
        assertTrue(report.contains("Energy Usage: " + energy + " kWh"));
        assertTrue(report.contains("Archived alerts: " + alerts));
        assertEquals(List.of("Archived " + alerts + " alerts", "Daily Report"),
                report.subList(report.indexOf("---") + 1, report.indexOf("---") + 3),
                "archive must always happen before the report (legacy issue C6)");
        for (String chamber : chambers) {
            assertTrue(report.stream().anyMatch(line -> line.startsWith(chamber + " -> Alerts=" + CYCLES
                    + ", Actions=" + CYCLES + ", Last Action=Cooling Fan Activated")), chamber + " summary");
        }

        // Extension: every alert is a temperature alert, so all are CRITICAL.
        List<String> severities = Files.readAllLines(reportFile.resolveSibling(SeverityLog.FILE_NAME));
        assertTrue(severities.contains("CRITICAL: " + alerts));
        assertTrue(severities.contains("WARNING: 0"));
        assertTrue(severities.contains("CRITICAL: CH1 Tomato High Temp"));
        List<String> lines = console.toString(StandardCharsets.UTF_8).lines().toList();
        assertTrue(lines.stream().anyMatch(line -> line.startsWith(
                "Alerts by severity: INFO=0, WARNING=0, CRITICAL=" + alerts + " (saved to ")));
        assertFalse(lines.stream().filter(line -> line.startsWith("[")).anyMatch(line -> line.contains("CRITICAL")),
                "severity must not change the legacy alert lines");

        // Extension: the sensor log is saved, one line per reading (5 sensors per cycle).
        int readings = chambers.size() * CYCLES * SensorType.values().length;
        List<String> sensorLog = Files.readAllLines(reportFile.resolveSibling(SensorLog.FILE_NAME));
        assertEquals(readings, sensorLog.size());
        assertTrue(sensorLog.stream().anyMatch(line -> line.endsWith(" : Processing Tomato Temperature = 35.0")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Sensor log: " + readings + " readings (saved to ")));

        // Extension (spec 3.5): per-chamber water and energy, adding up to the daily report's totals.
        // No rule here costs water, so each chamber uses only its 2 L per cycle.
        List<String> usage = Files.readAllLines(reportFile.resolveSibling(ResourceUsageReport.FILE_NAME));
        for (String chamber : chambers) {
            assertTrue(usage.contains(chamber + ": " + 2 * CYCLES), chamber + " water");
        }
        assertTrue(usage.contains("Total: " + water));
        assertTrue(usage.contains("Total: " + energy));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Resource usage: water " + water + " L, energy "
                + energy + " kWh across " + chambers.size() + " chambers (saved to ")));

        // Extension (spec 3.5): nutrient doses per chamber. Nutrient is in range, so every chamber has 0.
        List<String> nutrients = Files.readAllLines(reportFile.resolveSibling(NutrientUsageReport.FILE_NAME));
        for (String chamber : chambers) {
            assertTrue(nutrients.contains(chamber + ": 0"), chamber + " nutrient doses");
        }
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Nutrient usage: 0 Nutrient Injector doses across "
                + chambers.size() + " chambers (saved to ")));

        // Extension (spec 3.5): the weekly crop report's totals add up to the daily report's.
        List<String> weekly = Files.readAllLines(reportFile.resolveSibling(WeeklyCropReport.FILE_NAME));
        assertTrue(weekly.contains("Total -> ChamberDays=" + chambers.size() + ", Alerts=" + alerts + ", Actions=" + alerts
                + ", Water=" + water + " L, Energy=" + energy + " kWh"), String.join("\n", weekly));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Weekly crop report: ")
                && line.contains(", 1 of 7 days recorded (saved to ")));
    }

    /**
     * Spec 3.4 Sensor Failure Alert: an impossible reading raises a CRITICAL
     * alert, is not stored, and triggers no device. Also shows a config folder
     * selecting a single chamber.
     */
    @Test
    void invalidReadingRaisesSensorFailureAlert() throws Exception {
        Path config = dir.resolve("config");
        Files.createDirectories(config);
        Files.writeString(config.resolve("chambers.csv"), "CH1, Tomato\n");
        Map<SensorType, Double> readings = new HashMap<>(Map.of(
                SensorType.TEMPERATURE, 25.0, SensorType.SOIL, 50.0,
                SensorType.LIGHT, 700.0, SensorType.NUTRIENT, 60.0));
        readings.put(SensorType.HUMIDITY, Double.NaN);
        PrintStream silent = new PrintStream(new ByteArrayOutputStream());
        Path reportFile = dir.resolve("archive").resolve("daily_report.txt");

        boolean ok = SVFRMS.createSimulation(TestFixtures.fixed(readings), Integer.MAX_VALUE, config,
                silent, silent, Clock.systemUTC()).run(reportFile);

        assertTrue(ok);
        List<String> report = Files.readAllLines(reportFile);
        assertTrue(report.contains("Generated Alerts: " + CYCLES));
        assertTrue(report.stream().anyMatch(line -> line.startsWith("CH1 -> Alerts=" + CYCLES
                + ", Actions=0, Last Action=No action, Last Alert=CH1 Tomato Humidity Sensor Failure")));
        List<String> severities = Files.readAllLines(reportFile.resolveSibling(SeverityLog.FILE_NAME));
        assertTrue(severities.contains("CRITICAL: " + CYCLES));
    }

    /** Spec 3.4: a sensor that does not respond raises a Sensor Failure alert; its chamber carries on. */
    @Test
    void unresponsiveSensorRaisesSensorFailureAlert() throws Exception {
        Path config = dir.resolve("config");
        Files.createDirectories(config);
        Files.writeString(config.resolve("chambers.csv"), "CH1, Tomato\n");
        PrintStream silent = new PrintStream(new ByteArrayOutputStream());
        Path reportFile = dir.resolve("archive").resolve("daily_report.txt");

        boolean ok = SVFRMS.createSimulation(type -> {
            if (type == SensorType.HUMIDITY) {
                throw new IllegalStateException("sensor offline");
            }
            return TestFixtures.HOT_CHAMBER.get(type);
        }, Integer.MAX_VALUE, config, silent, silent, Clock.systemUTC()).run(reportFile);

        assertTrue(ok, "a broken sensor must not stop its chamber");
        List<String> report = Files.readAllLines(reportFile);
        // per cycle: one High Temp alert, then one Sensor Failure alert
        assertTrue(report.contains("Generated Alerts: " + 2 * CYCLES));
        assertTrue(report.contains("CH1 Tomato Humidity Sensor Failure (no response: sensor offline)"));
        assertTrue(report.stream().anyMatch(line -> line.startsWith("CH1 -> Alerts=" + 2 * CYCLES
                + ", Actions=" + CYCLES + ", Last Action=Cooling Fan Activated")));
    }

    @Test
    void failingChamberIsReportedNotSwallowed() throws Exception {
        // Legacy issue C11: a crashed chamber thread went unnoticed. A sensor fault
        // is now an alert, so simulate a crash with an Error instead.
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8);
        PrintStream silent = new PrintStream(new ByteArrayOutputStream());
        boolean ok = SVFRMS.createSimulation(type -> {
            throw new AssertionError("simulated crash");
        }, Integer.MAX_VALUE, null, silent, err, Clock.systemUTC())
                .run(dir.resolve("archive").resolve("daily_report.txt"));

        assertFalse(ok);
        String text = errors.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("ERROR: chamber CH1 failed"), text);
        assertFalse(text.contains("stopped after"), "a failed chamber is reported once, not again as a partial day");
    }

    /**
     * Legacy issue C11 on the interrupted path: run() is interrupted while
     * waiting for CH1, after CH2 has already crashed. CH2's error must still be
     * printed, not only a "partial day" warning.
     */
    @Test
    void chamberFailureIsReportedWhenTheRunIsInterrupted() throws Exception {
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8);
        PrintStream silent = new PrintStream(new ByteArrayOutputStream());

        // The reading log names the crop: CH2 (Lettuce) crashes, CH1 (Tomato) waits until interrupted.
        CountDownLatch crashed = new CountDownLatch(1);
        ReadingLog readingLog = message -> {
            if (message.contains("Lettuce")) {
                crashed.countDown();
                throw new AssertionError("simulated crash");
            }
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        CropRegistry crops = new CropRegistry(Map.of(), List.of());
        AlertStore store = new AlertStore();
        ChamberSummaryTracker summaries = new ChamberSummaryTracker();
        AlertPublisher alerts = new AlertPublisher(err);
        ResourceMeter resources = new ResourceMeter();
        ControlService control = new ControlService(10, List.of(summaries), err);
        SensorProcessor processor = new SensorProcessor(crops, alerts, resources, control, readingLog,
                new FarmConfigLoader(null, null).loadSensorRanges("sensors.csv"));
        FarmSimulation simulation = new FarmSimulation(
                List.of(new ChamberAssignment("CH1", "Tomato"), new ChamberAssignment("CH2", "Lettuce")), 2,
                TestFixtures.fixed(TestFixtures.HOT_CHAMBER), processor, alerts, resources, control,
                new DailyReport(store, resources, summaries, silent, Clock.systemUTC()),
                List.of(), silent, err);

        AtomicBoolean ok = new AtomicBoolean(true);
        Thread main = new Thread(() -> ok.set(simulation.run(dir.resolve("archive").resolve("daily_report.txt"))));
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            main.start();
            crashed.await();
            main.interrupt();
            main.join();
        });

        assertFalse(ok.get());
        String text = errors.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("ERROR: chamber CH2 failed"), text);
        assertFalse(text.contains("chamber CH2 stopped after"), "reported once, with its error");
    }

    /** An interrupted run still stops every thread, writes the report and keeps the interrupt flag. */
    @Test
    void interruptedRunStillWritesTheReport() throws Exception {
        PrintStream silent = new PrintStream(new ByteArrayOutputStream());
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8);
        Path reportFile = dir.resolve("archive").resolve("daily_report.txt");

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            Thread.currentThread().interrupt();
            boolean ok = SVFRMS.createSimulation(TestFixtures.fixed(TestFixtures.HOT_CHAMBER), Integer.MAX_VALUE,
                    null, silent, err, Clock.systemUTC()).run(reportFile);
            assertTrue(Thread.interrupted(), "interrupt flag must be kept");
            assertFalse(ok);
        });

        // Which chambers stop early depends on timing, but an interrupted chamber is
        // a partial day, never a failure (even if the control thread stopped first).
        String text = errors.toString(StandardCharsets.UTF_8);
        assertFalse(text.contains("failed"), text);
        assertTrue(Files.exists(reportFile));
        assertTrue(Files.exists(reportFile.resolveSibling(SeverityLog.FILE_NAME)));
    }

    /**
     * If the control thread dies after the last request (an Error inside a
     * device), no chamber notices, so run() itself must report the failure.
     */
    @Test
    void controlThreadCrashFailsTheRun() throws Exception {
        Device crashing = new Device() {
            public String name() {
                return "Crashing";
            }

            public Set<Effect> effects() {
                return Set.of();
            }

            public String apply(ChamberState state) {
                throw new AssertionError("simulated crash");
            }
        };
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8);
        PrintStream silent = new PrintStream(new ByteArrayOutputStream());

        // Only the very first reading is hot, so exactly one request reaches the device.
        AtomicBoolean first = new AtomicBoolean(true);
        SensorSource sensors = type -> type == SensorType.TEMPERATURE && first.getAndSet(false)
                ? 35.0 : TestFixtures.HOT_CHAMBER.get(type) - (type == SensorType.TEMPERATURE ? 10 : 0);
        CropRegistry crops = new CropRegistry(Map.of(), List.of(new ThresholdRule(SensorType.TEMPERATURE,
                ThresholdRule.Direction.HIGH, 30, crashing, 0, 0, Severity.CRITICAL, "{crop} High Temp")));
        AlertStore store = new AlertStore();
        ChamberSummaryTracker summaries = new ChamberSummaryTracker();
        AlertPublisher alerts = new AlertPublisher(err);
        alerts.subscribe(summaries);
        alerts.subscribe(store);
        ResourceMeter resources = new ResourceMeter();
        ControlService control = new ControlService(10, List.of(summaries), err);
        SensorLog sensorLog = new SensorLog(Clock.systemUTC());
        SensorProcessor processor = new SensorProcessor(crops, alerts, resources, control,
                sensorLog, new FarmConfigLoader(null, null).loadSensorRanges("sensors.csv"));
        FarmSimulation simulation = new FarmSimulation(List.of(new ChamberAssignment("CH1", "Tomato")), 1, sensors,
                processor, alerts, resources, control,
                new DailyReport(store, resources, summaries, silent, Clock.systemUTC()),
                List.of(new SeverityLog(), sensorLog), silent, err);

        boolean ok = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> simulation.run(dir.resolve("archive").resolve("daily_report.txt")));

        assertFalse(ok);
        String text = errors.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Control thread failed"), text);
    }

    /**
     * A device that throws is reported and the control thread keeps going, but
     * the report then misses that action, so the run must still fail (exit status 1).
     */
    @Test
    void failingDeviceFailsTheRun() throws Exception {
        Device broken = new Device() {
            public String name() {
                return "Broken";
            }

            public Set<Effect> effects() {
                return Set.of(Effect.lowers(SensorType.TEMPERATURE));
            }

            public String apply(ChamberState state) {
                throw new IllegalStateException("device fault");
            }
        };
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8);
        PrintStream silent = new PrintStream(new ByteArrayOutputStream());

        // One alert and one device request per cycle (35C > 30C).
        CropRegistry crops = new CropRegistry(Map.of(), List.of(new ThresholdRule(SensorType.TEMPERATURE,
                ThresholdRule.Direction.HIGH, 30, broken, 0, 0, Severity.CRITICAL, "{crop} High Temp")));
        AlertStore store = new AlertStore();
        ChamberSummaryTracker summaries = new ChamberSummaryTracker();
        AlertPublisher alerts = new AlertPublisher(err);
        alerts.subscribe(summaries);
        alerts.subscribe(store);
        ResourceMeter resources = new ResourceMeter();
        ControlService control = new ControlService(10, List.of(summaries), err);
        SensorLog sensorLog = new SensorLog(Clock.systemUTC());
        SensorProcessor processor = new SensorProcessor(crops, alerts, resources, control,
                sensorLog, new FarmConfigLoader(null, null).loadSensorRanges("sensors.csv"));
        FarmSimulation simulation = new FarmSimulation(List.of(new ChamberAssignment("CH1", "Tomato")), 1,
                TestFixtures.fixed(TestFixtures.HOT_CHAMBER), processor, alerts, resources, control,
                new DailyReport(store, resources, summaries, silent, Clock.systemUTC()),
                List.of(sensorLog), silent, err);

        boolean ok = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> simulation.run(dir.resolve("archive").resolve("daily_report.txt")));

        assertFalse(ok);
        assertEquals(CYCLES, store.generatedCount(), "every chamber cycle still ran");
        String text = errors.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains(CYCLES + " device actions failed"), text);
    }

    /**
     * A listener that throws no longer stops the chamber, but the report may
     * then be short, so the run must still fail (exit status 1).
     */
    @Test
    void failingAlertListenerFailsTheRun() throws Exception {
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8);
        PrintStream silent = new PrintStream(new ByteArrayOutputStream());

        // One alert per cycle (35C > 30C), no device.
        CropRegistry crops = new CropRegistry(Map.of(), List.of(new ThresholdRule(SensorType.TEMPERATURE,
                ThresholdRule.Direction.HIGH, 30, null, 0, 0, Severity.CRITICAL, "{crop} High Temp")));
        AlertStore store = new AlertStore();
        ChamberSummaryTracker summaries = new ChamberSummaryTracker();
        AlertPublisher alerts = new AlertPublisher(err);
        alerts.subscribe(alert -> {
            throw new IllegalStateException("broken listener");
        });
        alerts.subscribe(store);
        ResourceMeter resources = new ResourceMeter();
        ControlService control = new ControlService(10, List.of(summaries), err);
        SensorLog sensorLog = new SensorLog(Clock.systemUTC());
        SensorProcessor processor = new SensorProcessor(crops, alerts, resources, control,
                sensorLog, new FarmConfigLoader(null, null).loadSensorRanges("sensors.csv"));
        FarmSimulation simulation = new FarmSimulation(List.of(new ChamberAssignment("CH1", "Tomato")), 1,
                TestFixtures.fixed(TestFixtures.HOT_CHAMBER), processor, alerts, resources, control,
                new DailyReport(store, resources, summaries, silent, Clock.systemUTC()),
                List.of(sensorLog), silent, err);

        boolean ok = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> simulation.run(dir.resolve("archive").resolve("daily_report.txt")));

        assertFalse(ok);
        assertEquals(CYCLES, store.generatedCount(), "every alert still reaches the other listeners");
        String text = errors.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains(CYCLES + " alert deliveries failed"), text);
    }
}
