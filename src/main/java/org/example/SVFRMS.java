package org.example;

import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Random;

import org.example.alert.AlertPublisher;
import org.example.alert.AlertStore;
import org.example.alert.ConsoleAlertPrinter;
import org.example.app.ChamberAssignment;
import org.example.app.FarmConfigLoader;
import org.example.app.FarmSimulation;
import org.example.crop.CropRegistry;
import org.example.device.ControlService;
import org.example.device.CoolingFan;
import org.example.device.Dehumidifier;
import org.example.device.DeviceRegistry;
import org.example.device.GrowLight;
import org.example.device.Humidifier;
import org.example.device.NutrientInjector;
import org.example.device.WaterPump;
import org.example.report.ChamberSummaryTracker;
import org.example.report.DailyReport;
import org.example.report.ResourceMeter;
import org.example.report.SensorLog;
import org.example.report.SeverityLog;
import org.example.sensor.RandomSensorSource;
import org.example.sensor.SensorProcessor;
import org.example.sensor.SensorRanges;
import org.example.sensor.SensorSource;

/**
 * Singapore Vertical Farming Research Management System - entry point.
 *
 * The only place that creates objects and connects them (composition root).
 * Every other class receives what it needs through its constructor.
 */
public final class SVFRMS {
    public static final Path REPORT_FILE = Path.of("archive", "daily_report.txt");
    private static final int CONTROL_QUEUE_CAPACITY = 5;

    private SVFRMS() {
    }

    /**
     * Usage: java -jar run.jar [config-folder]
     *
     * With a config folder, a crops.csv, chambers.csv or sensors.csv inside it replaces
     * the built-in file, e.g. "java -jar run.jar demo" adds the Kale, Bok Choy and Bell Pepper chambers.
     */
    public static void main(String[] args) {
        if (args.length > 1) {
            usageError("Too many arguments.");
        }
        Path configDir = args.length == 1 ? configFolder(args[0]) : null;

        FarmSimulation simulation;
        try {
            simulation = createSimulation(new RandomSensorSource(new Random()), Integer.MAX_VALUE,
                    configDir, System.out, System.err, Clock.systemDefaultZone());
        } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException e) {
            System.err.println("Invalid configuration: " + e.getMessage());
            System.exit(2);
            return;
        }
        if (!simulation.run(REPORT_FILE)) {
            System.exit(1);
        }
    }

    /** The folder named on the command line, or exits with status 2 if it is not a usable config folder. */
    private static Path configFolder(String arg) {
        Path dir;
        try {
            dir = Path.of(arg);
        } catch (InvalidPathException e) {
            usageError("Not a valid folder name: " + arg);
            return null;
        }
        if (!Files.isDirectory(dir)) {
            usageError("Config folder not found: " + dir.toAbsolutePath());
        }
        if (FarmConfigLoader.CONFIG_FILES.stream().noneMatch(name -> Files.isRegularFile(dir.resolve(name)))) {
            // Otherwise a wrong folder would silently run the built-in files.
            usageError("Config folder has none of " + String.join(", ", FarmConfigLoader.CONFIG_FILES) + ": "
                    + dir.toAbsolutePath());
        }
        return dir;
    }

    private static void usageError(String message) {
        System.err.println(message);
        System.err.println("Usage: java -jar run.jar [config-folder]");
        System.exit(2);
    }

    /** Built-in config files, errors to System.err, system clock. */
    public static FarmSimulation createSimulation(SensorSource sensors, int maxChamberThreads, PrintStream out) {
        return createSimulation(sensors, maxChamberThreads, null, out, System.err, Clock.systemDefaultZone());
    }

    /**
     * Builds the whole system.
     *
     * @param maxChamberThreads chambers run in parallel up to this many threads (1 = one at a time)
     * @param configDir         folder whose crops.csv / chambers.csv / sensors.csv override the built-in ones, or null
     */
    public static FarmSimulation createSimulation(SensorSource sensors, int maxChamberThreads, Path configDir,
                                                  PrintStream out, PrintStream err, Clock clock) {
        DeviceRegistry devices = new DeviceRegistry()
                .register(new CoolingFan())
                .register(new WaterPump())
                .register(new NutrientInjector())
                .register(new Humidifier())
                .register(new Dehumidifier())
                .register(new GrowLight());

        FarmConfigLoader config = new FarmConfigLoader(configDir, err);
        CropRegistry crops = config.loadCrops(FarmConfigLoader.CROPS_FILE, devices);
        List<ChamberAssignment> chambers = config.loadChambers(FarmConfigLoader.CHAMBERS_FILE, crops);
        SensorRanges sensorRanges = config.loadSensorRanges(FarmConfigLoader.SENSORS_FILE);

        AlertStore alertStore = new AlertStore();
        ChamberSummaryTracker summaries = new ChamberSummaryTracker();
        AlertPublisher alerts = new AlertPublisher();
        alerts.subscribe(summaries);                     // same order as the legacy code:
        alerts.subscribe(alertStore);                    // track, store, then print
        alerts.subscribe(new ConsoleAlertPrinter(out));
        SeverityLog severityLog = new SeverityLog();      // extension: alerts by severity, own file
        alerts.subscribe(severityLog);

        ResourceMeter resources = new ResourceMeter();
        ControlService control = new ControlService(CONTROL_QUEUE_CAPACITY, summaries, out, err);
        SensorProcessor processor = new SensorProcessor(crops, devices, alerts, resources, control, new SensorLog(clock),
                sensorRanges);
        DailyReport report = new DailyReport(alertStore, resources, summaries, out, clock);

        return new FarmSimulation(chambers, maxChamberThreads, sensors, processor, resources, control, report,
                severityLog, out, err);
    }
}
