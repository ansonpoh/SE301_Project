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

import org.example.alert.AlertPublisher;
import org.example.device.ControlService;
import org.example.report.DailyReport;
import org.example.report.DayEndOutput;
import org.example.sensor.ChamberMonitor;
import org.example.sensor.SensorProcessor;
import org.example.sensor.SensorSource;
import org.example.sensor.UsageMeter;

/**
 * Runs one simulated day:
 *   1. start the control thread
 *   2. run every chamber monitor and wait for all of them
 *   3. stop the control thread once its queue is drained
 *   4. archive alerts, then print the report (fixed order), then save it
 *      and every other end-of-day output (alerts by severity, sensor log)
 *
 * Chamber failures are read from each chamber's Future once every thread has
 * stopped and reported, instead of a thread dying silently as in the legacy
 * code (issue C11).
 */
public class FarmSimulation {
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final List<ChamberAssignment> chambers;
    private final int maxChamberThreads;
    private final SensorSource sensors;
    private final SensorProcessor processor;
    private final AlertPublisher alerts;
    private final UsageMeter resources;
    private final ControlService control;
    private final DailyReport report;
    private final List<DayEndOutput> dayEndOutputs;
    private final PrintStream out;
    private final PrintStream err;

    /**
     * @param maxChamberThreads how many chambers run at once; 1 runs them one
     *                          after another (used for the repeatable baseline)
     * @param dayEndOutputs     files saved next to the daily report, each with one
     *                          console line printed after the legacy output, so every
     *                          legacy console line stays the same
     */
    public FarmSimulation(List<ChamberAssignment> chambers, int maxChamberThreads, SensorSource sensors,
                          SensorProcessor processor, AlertPublisher alerts, UsageMeter resources, ControlService control,
                          DailyReport report, List<DayEndOutput> dayEndOutputs,
                          PrintStream out, PrintStream err) {
        this.chambers = List.copyOf(chambers);
        this.maxChamberThreads = maxChamberThreads;
        this.sensors = sensors;
        this.processor = processor;
        this.alerts = alerts;
        this.resources = resources;
        this.control = control;
        this.report = report;
        this.dayEndOutputs = List.copyOf(dayEndOutputs);
        this.out = out;
        this.err = err;
    }

    /**
     * If the calling thread is interrupted, the chambers and the control thread
     * are stopped, the report is still written, and the interrupt flag is set
     * again on return.
     *
     * @return true if every chamber finished without error, the control thread
     *         ran every request and stopped cleanly, every alert reached every
     *         listener, every device and action listener completed, every
     *         report file was saved, and nothing was interrupted
     */
    public boolean run(Path reportFile) {
        ExecutorService controlThread = Executors.newSingleThreadExecutor();
        ExecutorService chamberThreads = Executors.newFixedThreadPool(
                Math.max(1, Math.min(maxChamberThreads, chambers.size())));
        Future<?> controlTask = controlThread.submit(control);
        List<ChamberMonitor> monitors = new ArrayList<>();
        List<Future<?>> running = new ArrayList<>();
        for (ChamberAssignment chamber : chambers) {
            ChamberMonitor monitor = new ChamberMonitor(chamber.chamberId(), chamber.crop(), sensors, processor,
                    resources);
            monitors.add(monitor);
            running.add(chamberThreads.submit(monitor));
        }

        boolean interrupted = false;
        try {
            awaitChambers(running);
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
        List<String> failedChambers = reportFailedChambers(monitors, running);
        reportUnfinishedChambers(monitors, failedChambers);

        int failedDeliveries = alerts.failedDeliveries();
        if (failedDeliveries > 0) {
            err.println("ERROR: " + failedDeliveries + " alert deliveries failed; the report totals may be short.");
        }
        int failedActions = control.failedActions();
        if (failedActions > 0) {
            err.println("ERROR: " + failedActions + " device actions failed; the report totals may be short.");
        }

        report.archiveAlerts();
        report.print();
        out.println("System finished sensor processing.");
        boolean allSaved = report.save(reportFile);
        for (DayEndOutput output : dayEndOutputs) {
            Path file = reportFile.resolveSibling(output.fileName());
            try {
                output.save(file);
                out.println(output.summaryLine() + " (saved to " + file.toAbsolutePath() + ")");
            } catch (IOException e) {
                err.println("Could not save " + file + ": " + e);
                allSaved = false;
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return failedChambers.isEmpty() && shutdownOk && failedDeliveries == 0 && failedActions == 0 && allSaved
                && !interrupted;
    }

    /**
     * Waits for the pool; if it does not finish in time, interrupts it and waits
     * again, so its threads have stopped before the report is written.
     *
     * @return false if the pool did not finish in time and had to be stopped
     */
    private boolean awaitStopped(ExecutorService pool, String name) throws InterruptedException {
        if (pool.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return true;
        }
        err.println(name + " did not finish in time; stopping.");
        pool.shutdownNow();
        if (!pool.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            err.println(name + " still running after being stopped; anything they do from now on is not in the report.");
        }
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

    /**
     * Names every chamber that did not process its whole day (it failed, or was
     * stopped by an interrupt or the shutdown timeout), so the report's totals
     * are never silently a mix of full and partial days. A chamber that failed
     * has already been reported with its error, so it is not named again.
     */
    private void reportUnfinishedChambers(List<ChamberMonitor> monitors, List<String> failedChambers) {
        for (ChamberMonitor monitor : monitors) {
            if (monitor.cyclesDone() < ChamberMonitor.CYCLES && !failedChambers.contains(monitor.chamberId())) {
                err.println("WARNING: chamber " + monitor.chamberId() + " stopped after " + monitor.cyclesDone()
                        + " of " + ChamberMonitor.CYCLES + " cycles; its totals in the report are partial.");
            }
        }
    }

    /**
     * Waits for every chamber. A chamber's exception is not reported here but
     * by reportFailedChambers() once all threads have stopped, so it is never
     * lost when this wait is interrupted before reaching that chamber.
     */
    private static void awaitChambers(List<Future<?>> running) throws InterruptedException {
        for (Future<?> chamber : running) {
            try {
                chamber.get();
            } catch (ExecutionException | CancellationException e) {
                // reported by reportFailedChambers()
            }
        }
    }

    /** Prints every chamber that threw, with its error (issue C11). @return their ids */
    private List<String> reportFailedChambers(List<ChamberMonitor> monitors, List<Future<?>> running) {
        List<String> failed = new ArrayList<>();
        for (int i = 0; i < running.size(); i++) {
            if (running.get(i).state() == Future.State.FAILED) {
                failed.add(monitors.get(i).chamberId());
                err.println("ERROR: chamber " + monitors.get(i).chamberId() + " failed: "
                        + running.get(i).exceptionNow());
            }
        }
        return failed;
    }
}
