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
import org.example.report.DayStatus;
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
        List<RunningChamber> running = startChambers(chamberThreads);

        boolean interrupted = !awaitChambers(running, chamberThreads);
        Shutdown shutdown = shutDown(chamberThreads, controlThread, controlTask, interrupted);
        DayStatus day = checkDay(running, shutdown);
        boolean allSaved = saveOutputs(reportFile, day);

        if (shutdown.interrupted()) {
            Thread.currentThread().interrupt();
        }
        return day == DayStatus.COMPLETE && allSaved;
    }

    /** A chamber's monitor and the task running it. */
    private record RunningChamber(ChamberMonitor monitor, Future<?> task) {
    }

    /**
     * How the threads stopped.
     *
     * @param clean       the control thread ran every request and every thread stopped in time
     * @param interrupted the calling thread was interrupted at some point
     */
    private record Shutdown(boolean clean, boolean interrupted) {
    }

    /** Submits one ChamberMonitor per row of chambers.csv. */
    private List<RunningChamber> startChambers(ExecutorService chamberThreads) {
        List<RunningChamber> running = new ArrayList<>();
        for (ChamberAssignment chamber : chambers) {
            ChamberMonitor monitor = new ChamberMonitor(chamber.chamberId(), chamber.crop(), sensors, processor,
                    resources);
            running.add(new RunningChamber(monitor, chamberThreads.submit(monitor)));
        }
        return running;
    }

    /**
     * Stops the control thread once its queue is drained, then waits for both
     * pools, so no thread is still running when the report is written.
     *
     * @param interrupted whether the wait for the chambers was interrupted
     */
    private Shutdown shutDown(ExecutorService chamberThreads, ExecutorService controlThread, Future<?> controlTask,
                              boolean interrupted) {
        chamberThreads.shutdown();
        boolean clean = stopControlThread(controlThread, interrupted);
        // Clear the flag stop() may have set, so the waits below still work.
        interrupted |= Thread.interrupted();
        try {
            clean &= awaitStopped(chamberThreads, "Chamber threads");
            clean &= awaitStopped(controlThread, "Control thread");
        } catch (InterruptedException e) {
            interrupted = true;
            chamberThreads.shutdownNow();
            controlThread.shutdownNow();
        }
        clean &= controlFinished(controlTask);
        return new Shutdown(clean, interrupted);
    }

    /**
     * Queues the stop marker behind every pending request; if that is not
     * possible, interrupts the consumer instead.
     *
     * @return false if the consumer had to be interrupted
     */
    private boolean stopControlThread(ExecutorService controlThread, boolean interrupted) {
        boolean stopped = !interrupted && control.stop(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!stopped) {
            // The stop marker was not queued (interrupted, or a device hung with
            // the queue full), so interrupt the consumer instead.
            if (!interrupted) {
                err.println("Control thread did not accept the stop request in time; stopping.");
            }
            controlThread.shutdownNow();
        }
        controlThread.shutdown();
        return stopped;
    }

    /**
     * Prints everything that went wrong during the day. Today's figures are
     * short if anything did; an output that keeps days (the weekly report)
     * then leaves today out.
     */
    private DayStatus checkDay(List<RunningChamber> running, Shutdown shutdown) {
        List<String> failedChambers = reportFailedChambers(running);
        boolean allChambersFinished = reportUnfinishedChambers(running, failedChambers);

        int failedDeliveries = alerts.failedDeliveries();
        if (failedDeliveries > 0) {
            err.println("ERROR: " + failedDeliveries + " alert deliveries failed; the report totals may be short.");
        }
        int failedActions = control.failedActions();
        if (failedActions > 0) {
            err.println("ERROR: " + failedActions + " device actions failed; the report totals may be short.");
        }

        boolean completeDay = failedChambers.isEmpty() && allChambersFinished && shutdown.clean()
                && failedDeliveries == 0 && failedActions == 0 && !shutdown.interrupted();
        return completeDay ? DayStatus.COMPLETE : DayStatus.INCOMPLETE;
    }

    /**
     * Archives the alerts, then prints and saves the daily report (fixed order),
     * then saves every other end-of-day output next to it.
     *
     * @return false if any file could not be saved
     */
    private boolean saveOutputs(Path reportFile, DayStatus day) {
        report.archiveAlerts();
        report.print();
        out.println("System finished sensor processing.");
        boolean allSaved = report.save(reportFile);
        for (DayEndOutput output : dayEndOutputs) {
            Path file = reportFile.resolveSibling(output.fileName());
            try {
                output.save(file, day);
                out.println(output.summaryLine() + " (saved to " + file.toAbsolutePath() + ")");
            } catch (IOException e) {
                err.println("Could not save " + file + ": " + e);
                allSaved = false;
            }
        }
        return allSaved;
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
     *
     * @return true if every chamber ran all its cycles
     */
    private boolean reportUnfinishedChambers(List<RunningChamber> running, List<String> failedChambers) {
        boolean allFinished = true;
        for (RunningChamber chamber : running) {
            ChamberMonitor monitor = chamber.monitor();
            if (monitor.cyclesDone() < ChamberMonitor.CYCLES) {
                allFinished = false;
                if (!failedChambers.contains(monitor.chamberId())) {
                    err.println("WARNING: chamber " + monitor.chamberId() + " stopped after " + monitor.cyclesDone()
                            + " of " + ChamberMonitor.CYCLES + " cycles; its totals in the report are partial.");
                }
            }
        }
        return allFinished;
    }

    /**
     * Waits for every chamber. A chamber's exception is not reported here but
     * by reportFailedChambers() once all threads have stopped, so it is never
     * lost when this wait is interrupted before reaching that chamber.
     *
     * @return false if the wait was interrupted; the chambers are then interrupted too
     */
    private static boolean awaitChambers(List<RunningChamber> running, ExecutorService chamberThreads) {
        try {
            for (RunningChamber chamber : running) {
                try {
                    chamber.task().get();
                } catch (ExecutionException | CancellationException e) {
                    // reported by reportFailedChambers()
                }
            }
            return true;
        } catch (InterruptedException e) {
            // Interrupt the chambers first, so none is left blocked on a full
            // queue after the control thread has gone.
            chamberThreads.shutdownNow();
            return false;
        }
    }

    /** Prints every chamber that threw, with its error (issue C11). @return their ids */
    private List<String> reportFailedChambers(List<RunningChamber> running) {
        List<String> failed = new ArrayList<>();
        for (RunningChamber chamber : running) {
            if (chamber.task().state() == Future.State.FAILED) {
                failed.add(chamber.monitor().chamberId());
                err.println("ERROR: chamber " + chamber.monitor().chamberId() + " failed: "
                        + chamber.task().exceptionNow());
            }
        }
        return failed;
    }
}
