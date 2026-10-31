package org.example;

import java.io.PrintStream;
import java.nio.file.Path;
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
import org.example.sensor.RandomSensorSource;
import org.example.sensor.SensorProcessor;
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

    public static void main(String[] args) throws InterruptedException {
        FarmSimulation simulation = createSimulation(
                new RandomSensorSource(new Random()), Integer.MAX_VALUE, System.out);
        if (!simulation.run(REPORT_FILE)) {
            System.exit(1);
        }
    }

    /**
     * Builds the whole system.
     *
     * @param maxChamberThreads chambers run in parallel up to this many threads (1 = one at a time)
     */
    public static FarmSimulation createSimulation(SensorSource sensors, int maxChamberThreads, PrintStream out) {
        DeviceRegistry devices = new DeviceRegistry()
                .register(new CoolingFan())
                .register(new WaterPump())
                .register(new NutrientInjector())
                .register(new Humidifier())
                .register(new Dehumidifier())
                .register(new GrowLight());

        FarmConfigLoader config = new FarmConfigLoader();
        CropRegistry crops = config.loadCrops("crops.csv", devices);
        List<ChamberAssignment> chambers = config.loadChambers("chambers.csv");

        AlertStore alertStore = new AlertStore();
        ChamberSummaryTracker summaries = new ChamberSummaryTracker();
        AlertPublisher alerts = new AlertPublisher();
        alerts.subscribe(summaries);                     // same order as the legacy code:
        alerts.subscribe(alertStore);                    // track, store, then print
        alerts.subscribe(new ConsoleAlertPrinter(out));

        ResourceMeter resources = new ResourceMeter();
        ControlService control = new ControlService(CONTROL_QUEUE_CAPACITY, summaries, out);
        SensorProcessor processor = new SensorProcessor(crops, devices, alerts, resources, control, new SensorLog());
        DailyReport report = new DailyReport(alertStore, resources, summaries, out);

        return new FarmSimulation(chambers, maxChamberThreads, sensors, processor, resources, control, report, out);
    }
}
