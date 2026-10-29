package org.example.app;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.example.alert.Alert;
import org.example.alert.AlertStore;
import org.example.control.Activation;
import org.example.control.ControlQueue;
import org.example.control.ControlWorker;
import org.example.event.EventBus;
import org.example.metrics.ChamberActivityTracker;
import org.example.metrics.ReadingStatistics;
import org.example.metrics.Resource;
import org.example.metrics.ResourceMeter;
import org.example.model.Chamber;
import org.example.model.SensorReading;
import org.example.report.ChamberSnapshot;
import org.example.report.FarmSnapshot;
import org.example.rule.RuleEngine;
import org.example.simulation.ChamberMonitor;
import org.example.simulation.MonitoringContext;
import org.example.simulation.SensorSource;

/**
 * Facade over the monitoring subsystem: wires the event buses, stores and worker threads, and
 * exposes a small API (run, archive, snapshot) to the application.
 * <p>
 * Each instance owns its state, unlike the legacy {@code public static} fields shared by
 * everything, so runs are isolated from each other (and testable).
 */
public final class FarmSystem {

    private final List<Chamber> chambers;
    private final SimulationSettings settings;

    private final EventBus<SensorReading> readings = new EventBus<>();
    private final EventBus<Alert> alerts = new EventBus<>();
    private final EventBus<Activation> activations = new EventBus<>();

    private final AlertStore alertStore = new AlertStore();
    private final ChamberActivityTracker activityTracker = new ChamberActivityTracker();
    private final ReadingStatistics readingStatistics = new ReadingStatistics();
    private final ResourceMeter meter = new ResourceMeter();
    private final ControlQueue controlQueue;
    private final MonitoringContext monitoringContext;

    public FarmSystem(List<Chamber> chambers, SensorSource sensors, RuleEngine rules, SimulationSettings settings) {
        if (chambers.isEmpty()) {
            throw new IllegalArgumentException("At least one chamber is required");
        }
        this.chambers = List.copyOf(chambers);
        this.settings = settings;
        this.controlQueue = new ControlQueue(settings.controlQueueCapacity());
        this.monitoringContext = new MonitoringContext(sensors, rules, readings, alerts, controlQueue, meter,
                settings.baselineUsagePerCycle());

        readings.subscribe(readingStatistics::onReading);
        alerts.subscribe(alertStore::add);
        alerts.subscribe(activityTracker::onAlert);
        activations.subscribe(activityTracker::onActivation);
    }

    public void onAlert(Consumer<? super Alert> listener) {
        alerts.subscribe(listener);
    }

    public void onActivation(Consumer<? super Activation> listener) {
        activations.subscribe(listener);
    }

    /**
     * Monitors all chambers concurrently and returns once every monitor has finished and every
     * queued device request has been carried out.
     *
     * @throws ExecutionException if a monitor or the control worker failed
     */
    public void runMonitoring() throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(chambers.size() + 1);
        try {
            Future<?> worker = executor.submit(new ControlWorker(controlQueue, meter, activations));
            List<ChamberMonitor> monitors = chambers.stream()
                    .map(chamber -> new ChamberMonitor(chamber, settings.cycles(), monitoringContext))
                    .toList();
            for (Future<Integer> monitor : executor.invokeAll(monitors)) {
                monitor.get();
            }
            controlQueue.close();
            worker.get();
        } finally {
            executor.shutdownNow();
        }
    }

    /** @return the number of alerts moved to the archive */
    public int archiveAlerts() {
        return alertStore.archiveActive();
    }

    public FarmSnapshot snapshot() {
        List<ChamberSnapshot> chamberSnapshots = chambers.stream()
                .map(chamber -> new ChamberSnapshot(chamber.id(), chamber.crop().name(),
                        chamber.state().snapshot(), activityTracker.activityOf(chamber.id())))
                .toList();
        Map<Resource, SortedMap<String, Long>> usage = new EnumMap<>(Resource.class);
        for (Resource resource : Resource.values()) {
            usage.put(resource, meter.byChamber(resource));
        }
        return new FarmSnapshot(Instant.now(), chamberSnapshots, alertStore.totalGenerated(),
                alertStore.activeAlerts().size(), alertStore.archivedAlerts(),
                readingStatistics.total(), readingStatistics.failed(), usage);
    }
}
