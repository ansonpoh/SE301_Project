package org.example;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.example.alert.AlertPublisher;
import org.example.alert.AlertStore;
import org.example.alert.ConsoleAlertPrinter;
import org.example.app.ChamberAssignment;
import org.example.app.FarmConfigLoader;
import org.example.app.FarmSimulation;
import org.example.crop.CropRegistry;
import org.example.device.ConsoleActionPrinter;
import org.example.device.ControlService;
import org.example.device.DeviceRegistry;
import org.example.device.NutrientInjector;
import org.example.report.ChamberSummaryTracker;
import org.example.report.DailyReport;
import org.example.report.NutrientUsageReport;
import org.example.report.ResourceMeter;
import org.example.report.ResourceUsageReport;
import org.example.report.SensorLog;
import org.example.report.SeverityLog;
import org.example.report.WeeklyCropReport;
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
    /** Config folder name that falls back to the copy built into run.jar. */
    private static final String DEMO = "demo";

    private SVFRMS() {
    }

    /**
     * Usage: java -jar run.jar [config-folder]
     *
     * With a config folder, a crops.csv, chambers.csv or sensors.csv inside it replaces
     * the built-in file, e.g. "java -jar run.jar demo" adds the Kale, Bok Choy and Bell Pepper chambers
     * and a stricter sensors.csv, so Sensor Failure alerts appear. "demo" uses the copy built
     * into run.jar unless a file or folder of that name exists in the working directory.
     */
    public static void main(String[] args) {
        if (args.length > 1) {
            usageError("Too many arguments.");
        }
        FarmSimulation simulation;
        try {
            Path configDir = args.length == 1 ? configFolder(args[0]) : null;
            simulation = createSimulation(new RandomSensorSource(new Random()), Integer.MAX_VALUE,
                    configDir, System.out, System.err, Clock.systemDefaultZone());
        } catch (IllegalArgumentException | UncheckedIOException e) {
            System.err.println("Invalid configuration: " + e.getMessage());
            System.exit(2);
            return;
        } catch (IllegalStateException e) {
            // Not the user's CSV files: a broken build (missing resource or demo) or a wiring mistake here.
            System.err.println("Internal error: " + e.getMessage());
            System.exit(3);
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
        if (dir.normalize().equals(Path.of(DEMO)) && !Files.exists(dir)) {
            return builtInDemoFolder();   // "demo", "./demo" or "demo/" with no demo folder here: use the jar's copy
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

    /**
     * The demo folder inside run.jar (src/main/resources/demo), so "java -jar run.jar demo"
     * works from any folder. Read through a zip file system when running from the jar.
     */
    static Path builtInDemoFolder() {
        URL url = SVFRMS.class.getResource("/" + DEMO + "/" + FarmConfigLoader.CHAMBERS_FILE);
        if (url == null) {
            throw new IllegalStateException("Missing resource: " + DEMO + "/" + FarmConfigLoader.CHAMBERS_FILE);
        }
        return demoFolderOf(url);
    }

    /**
     * The folder holding the demo chambers.csv at this URL. A jar's zip file system stays
     * open, because the config loader reads the demo files from it after this returns.
     */
    static Path demoFolderOf(URL url) {
        try {
            URI uri = url.toURI();
            if (uri.getScheme().equals("jar")) {
                FileSystem jar;
                try {
                    jar = FileSystems.newFileSystem(uri, Map.of());
                } catch (FileSystemAlreadyExistsException e) {
                    jar = FileSystems.getFileSystem(uri);
                }
                return jar.getPath("/" + DEMO);
            }
            return Path.of(uri).getParent();
        } catch (URISyntaxException | IOException | IllegalArgumentException
                | FileSystemNotFoundException | ProviderNotFoundException e) {
            // A packaging problem (e.g. a URL scheme with no file system), not the user's config: exit 3.
            throw new IllegalStateException("Could not open built-in " + DEMO + " folder: " + e, e);
        }
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
        // Devices are listed in META-INF/services, so adding one needs no change here.
        DeviceRegistry devices = DeviceRegistry.discover();

        FarmConfigLoader config = new FarmConfigLoader(configDir, err);
        CropRegistry crops = config.loadCrops(FarmConfigLoader.CROPS_FILE, devices);
        List<ChamberAssignment> chambers = config.loadChambers(FarmConfigLoader.CHAMBERS_FILE, crops);
        SensorRanges sensorRanges = config.loadSensorRanges(FarmConfigLoader.SENSORS_FILE);

        AlertStore alertStore = new AlertStore();
        ChamberSummaryTracker summaries = new ChamberSummaryTracker();
        AlertPublisher alerts = new AlertPublisher(err);
        alerts.subscribe(summaries);                     // same order as the legacy code:
        alerts.subscribe(alertStore);                    // track, store, then print
        alerts.subscribe(new ConsoleAlertPrinter(out));
        SeverityLog severityLog = new SeverityLog();      // extension: alerts by severity, own file
        alerts.subscribe(severityLog);

        ResourceMeter resources = new ResourceMeter();
        // extension: nutrient doses per chamber, own file
        NutrientUsageReport nutrientUsage = new NutrientUsageReport(NutrientInjector.NAME,
                chambers.stream().map(ChamberAssignment::chamberId).toList(), clock);
        // same order as the legacy code: track the action, then print it
        ControlService control = new ControlService(CONTROL_QUEUE_CAPACITY,
                List.of(summaries, new ConsoleActionPrinter(out), nutrientUsage), err);
        SensorLog sensorLog = new SensorLog(clock);
        SensorProcessor processor = new SensorProcessor(crops, alerts, resources, control, sensorLog, sensorRanges);
        DailyReport report = new DailyReport(alertStore, resources, summaries, out, clock);
        // extension: alerts, actions and usage per crop, own file
        WeeklyCropReport weekly = new WeeklyCropReport(chambers.stream()
                .collect(Collectors.toMap(ChamberAssignment::chamberId, ChamberAssignment::crop)),
                summaries, resources, clock);

        return new FarmSimulation(chambers, maxChamberThreads, sensors, processor, alerts, resources, control, report,
                List.of(severityLog, sensorLog, new ResourceUsageReport(resources, clock), nutrientUsage, weekly),
                out, err);
    }
}
