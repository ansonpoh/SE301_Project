package org.example.app;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.example.device.ControlService;
import org.example.report.DailyReport;
import org.example.report.ResourceMeter;
import org.example.report.SeverityLog;
import org.example.sensor.ChamberMonitor;
import org.example.sensor.SensorProcessor;
import org.example.sensor.SensorSource;

/**
 * Runs one simulated day:
 *   1. start the control thread
 *   2. run every chamber monitor and wait for all of them
 *   3. stop the control thread once its queue is drained
 *   4. archive alerts, then print the report (fixed order), then save it
 *      and the alert log by severity
 *
 * Chamber failures are caught through Future.get() and reported, instead of a
 * thread dying silently as in the legacy code (issue C11).
 */
public class FarmSimulation {
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;
    /**
     * Written next to the daily report. Only a one-line count per level is printed,
     * after the legacy output, so every legacy console line stays the same.
     */
    public static final String SEVERITY_FILE = "alerts_by_severity.txt";

    private final List<ChamberAssignment> chambers;
    private final int maxChamberThreads;
    private final SensorSource sensors;
    private final SensorProcessor processor;
    private final ResourceMeter resources;
    private final ControlService control;
    private final DailyReport report;
    private final SeverityLog severityLog;
    private final PrintStream out;
    private final PrintStream err;

    /**
     * @param maxChamberThreads how many chambers run at once; 1 runs them one
     *                          after another (used for the repeatable baseline)
     */
    public FarmSimulation(List<ChamberAssignment> chambers, int maxChamberThreads, SensorSource sensors,
                          SensorProcessor processor, ResourceMeter resources, ControlService control,
                          DailyReport report, SeverityLog severityLog, PrintStream out, PrintStream err) {
        this.chambers = List.copyOf(chambers);
        this.maxChamberThreads = maxChamberThreads;
        this.sensors = sensors;
        this.processor = processor;
        this.resources = resources;
        this.control = control;
        this.report = report;
        this.severityLog = severityLog;
        this.out = out;
        this.err = err;
    }

    /**
     * If the calling thread is interrupted, the chambers and the control thread
     * are stopped, the report is still written, and the interrupt flag is set
     * again on return.
     *
     * @return true if every chamber finished without error, the control thread
     *         ran every request and stopped cleanly, and nothing was interrupted
     */
    public boolean run(Path reportFile) {
        ExecutorService controlThread = Executors.newSingleThreadExecutor();
        ExecutorService chamberThreads = Executors.newFixedThreadPool(
                Math.max(1, Math.min(maxChamberThreads, chambers.size())));
        Future<?> controlTask = controlThread.submit(control);

        boolean allChambersOk = false;
        boolean interrupted = false;
        try {
            allChambersOk = runChambers(chamberThreads);
        } catch (InterruptedException e) {
            // Interrupt the chambers first, so none is left blocked on a full
            // queue after the control thread has gone.
            interrupted = true;
            chamberThreads.shutdownNow();
        }
        chamberThreads.shutdown();
        boolean shutdownOk = !interrupted && control.stop(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!shutdownOk) {
            // The stop marker was not queued (interrupted, or a device hung with
            // the queue full), so interrupt the consumer instead.
            if (!interrupted) {
                err.println("Control thread did not accept the stop request in time; stopping.");
            }
            controlThread.shutdownNow();
        }
        controlThread.shutdown();
        // Clear the flag stop() may have set, so the waits below still work.
        interrupted |= Thread.interrupted();
        try {
            shutdownOk &= awaitStopped(chamberThreads, "Chamber threads");
            shutdownOk &= awaitStopped(controlThread, "Control thread");
        } catch (InterruptedException e) {
            interrupted = true;
            chamberThreads.shutdownNow();
            controlThread.shutdownNow();
        }
        shutdownOk &= controlFinished(controlTask);

        report.archiveAlerts();
        report.print();
        out.println("System finished sensor processing.");
        report.save(reportFile);
        Path severityFile = reportFile.resolveSibling(SEVERITY_FILE);
        try {
            severityLog.save(severityFile);
            out.println("Alerts by severity: " + severityLog.summary()
                    + " (saved to " + severityFile.toAbsolutePath() + ")");
        } catch (IOException e) {
            err.println("Could not save " + severityFile + ": " + e);
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return allChambersOk && shutdownOk && !interrupted;
    }

    /** @return false if the pool did not finish in time and had to be stopped */
    private boolean awaitStopped(ExecutorService pool, String name) throws InterruptedException {
        if (pool.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return true;
        }
        err.println(name + " did not finish in time; stopping.");
        pool.shutdownNow();
        return false;
    }

    /**
     * The control thread's own outcome, like Future.get() for the chambers: an
     * Error inside a device ends it, and its unrun requests have been reported.
     *
     * @return false if it failed or never got to run
     */
    private boolean controlFinished(Future<?> controlTask) {
        if (!controlTask.isDone()) {
            return false;
        }
        try {
            controlTask.get();   // already done, so this does not block
            return true;
        } catch (ExecutionException | InterruptedException | CancellationException e) {
            return false;   // ControlService has already reported the failure and its unrun requests
        }
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
                err.println("ERROR: chamber " + chambers.get(i).chamberId() + " failed: " + e.getCause());
            }
        }
        return allOk;
    }
}
