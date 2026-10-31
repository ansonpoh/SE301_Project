package org.example.app;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.example.device.ControlService;
import org.example.report.DailyReport;
import org.example.report.ResourceMeter;
import org.example.sensor.ChamberMonitor;
import org.example.sensor.SensorProcessor;
import org.example.sensor.SensorSource;

/**
 * Runs one simulated day:
 *   1. start the control thread
 *   2. run every chamber monitor and wait for all of them
 *   3. stop the control thread once its queue is drained
 *   4. archive alerts, then print the report (fixed order), then save it
 *
 * Chamber failures are caught through Future.get() and reported, instead of a
 * thread dying silently as in the legacy code (issue C11).
 */
public class FarmSimulation {
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final List<ChamberAssignment> chambers;
    private final int maxChamberThreads;
    private final SensorSource sensors;
    private final SensorProcessor processor;
    private final ResourceMeter resources;
    private final ControlService control;
    private final DailyReport report;
    private final PrintStream out;

    /**
     * @param maxChamberThreads how many chambers run at once; 1 runs them one
     *                          after another (used for the repeatable baseline)
     */
    public FarmSimulation(List<ChamberAssignment> chambers, int maxChamberThreads, SensorSource sensors,
                          SensorProcessor processor, ResourceMeter resources, ControlService control,
                          DailyReport report, PrintStream out) {
        this.chambers = List.copyOf(chambers);
        this.maxChamberThreads = maxChamberThreads;
        this.sensors = sensors;
        this.processor = processor;
        this.resources = resources;
        this.control = control;
        this.report = report;
        this.out = out;
    }

    /** @return true if every chamber finished without error */
    public boolean run(Path reportFile) throws InterruptedException {
        ExecutorService controlThread = Executors.newSingleThreadExecutor();
        ExecutorService chamberThreads = Executors.newFixedThreadPool(
                Math.max(1, Math.min(maxChamberThreads, chambers.size())));
        boolean allChambersOk;
        try {
            controlThread.submit(control);
            allChambersOk = runChambers(chamberThreads);
        } finally {
            chamberThreads.shutdown();
            control.stop();
            controlThread.shutdown();
            if (!controlThread.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.err.println("Control thread did not finish in time; stopping it.");
                controlThread.shutdownNow();
            }
        }

        report.archiveAlerts();
        report.print();
        out.println("System finished sensor processing.");
        report.save(reportFile);
        return allChambersOk;
    }

    private boolean runChambers(ExecutorService pool) throws InterruptedException {
        List<Future<?>> running = new ArrayList<>();
        for (ChamberAssignment chamber : chambers) {
            running.add(pool.submit(new ChamberMonitor(
                    chamber.chamberId(), chamber.crop(), sensors, processor, resources)));
        }

        boolean allOk = true;
        for (int i = 0; i < running.size(); i++) {
            try {
                running.get(i).get();
            } catch (ExecutionException e) {
                allOk = false;
                System.err.println("ERROR: chamber " + chambers.get(i).chamberId() + " failed: " + e.getCause());
            }
        }
        return allOk;
    }
}
