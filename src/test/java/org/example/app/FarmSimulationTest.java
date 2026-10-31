package org.example.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.concurrent.atomic.AtomicBoolean;

import org.example.SVFRMS;
import org.example.TestFixtures;
import org.example.alert.AlertPublisher;
import org.example.alert.AlertStore;
import org.example.alert.Severity;
import org.example.crop.CropRegistry;
import org.example.crop.ThresholdRule;
import org.example.device.ControlService;
import org.example.device.Device;
import org.example.device.DeviceRegistry;
import org.example.model.ChamberState;
import org.example.model.SensorType;
import org.example.report.ChamberSummaryTracker;
import org.example.report.DailyReport;
import org.example.report.ResourceMeter;
import org.example.report.SensorLog;
import org.example.report.SeverityLog;
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

    /** Spec 4.5: demo/chambers.csv adds Kale, Bok Choy and Bell Pepper with no code change. */
    @Test
    void demoConfigRunsTheNewCrops() throws Exception {
        // +10 kWh per Tomato and Strawberry, +8 kWh per Kale and Bok Choy High Temp alert
        assertExactTotals(Path.of(System.getProperty("basedir", ".")).resolve("demo"), List.of("CH1", "CH2", "CH3", "CH4", "CH5", "CH6", "CH7"), 2 * 10 + 2 * 8);
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
        List<String> severities = Files.readAllLines(reportFile.resolveSibling(FarmSimulation.SEVERITY_FILE));
        assertTrue(severities.contains("CRITICAL: " + alerts));
        assertTrue(severities.contains("WARNING: 0"));
        assertTrue(severities.contains("CRITICAL: CH1 Tomato High Temp"));
        List<String> lines = console.toString(StandardCharsets.UTF_8).lines().toList();
        assertTrue(lines.stream().anyMatch(line -> line.startsWith(
                "Alerts by severity: INFO=0, WARNING=0, CRITICAL=" + alerts + " (saved to ")));
        assertFalse(lines.stream().filter(line -> line.startsWith("[")).anyMatch(line -> line.contains("CRITICAL")),
                "severity must not change the legacy alert lines");
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
        List<String> severities = Files.readAllLines(reportFile.resolveSibling(FarmSimulation.SEVERITY_FILE));
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
        PrintStream silent = new PrintStream(new ByteArrayOutputStream());
        boolean ok = SVFRMS.createSimulation(type -> {
            throw new AssertionError("simulated crash");
        }, Integer.MAX_VALUE, silent).run(dir.resolve("archive").resolve("daily_report.txt"));

        assertFalse(ok);
    }

    /** An interrupted run still stops every thread, writes the report and keeps the interrupt flag. */
    @Test
    void interruptedRunStillWritesTheReport() throws Exception {
        PrintStream silent = new PrintStream(new ByteArrayOutputStream());
        Path reportFile = dir.resolve("archive").resolve("daily_report.txt");

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            Thread.currentThread().interrupt();
            boolean ok = SVFRMS.createSimulation(TestFixtures.fixed(TestFixtures.HOT_CHAMBER), Integer.MAX_VALUE,
                    silent).run(reportFile);
            assertTrue(Thread.interrupted(), "interrupt flag must be kept");
            assertFalse(ok);
        });

        assertTrue(Files.exists(reportFile));
        assertTrue(Files.exists(reportFile.resolveSibling(FarmSimulation.SEVERITY_FILE)));
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
                ThresholdRule.Direction.HIGH, 30, "Crashing", 0, 0, Severity.CRITICAL, "{crop} High Temp")));
        DeviceRegistry devices = new DeviceRegistry().register(crashing);
        AlertStore store = new AlertStore();
        ChamberSummaryTracker summaries = new ChamberSummaryTracker();
        AlertPublisher alerts = new AlertPublisher();
        alerts.subscribe(summaries);
        alerts.subscribe(store);
        ResourceMeter resources = new ResourceMeter();
        ControlService control = new ControlService(10, summaries, silent, err);
        SensorProcessor processor = new SensorProcessor(crops, devices, alerts, resources, control,
                new SensorLog(Clock.systemUTC()), new FarmConfigLoader().loadSensorRanges("sensors.csv"));
        FarmSimulation simulation = new FarmSimulation(List.of(new ChamberAssignment("CH1", "Tomato")), 1, sensors,
                processor, resources, control, new DailyReport(store, resources, summaries, silent, Clock.systemUTC()),
                new SeverityLog(), silent, err);

        boolean ok = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> simulation.run(dir.resolve("archive").resolve("daily_report.txt")));

        assertFalse(ok);
        String text = errors.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Control thread failed"), text);
    }
}
