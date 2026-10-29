package org.example.simulation;

import java.time.Instant;
import java.util.concurrent.Callable;

import org.example.alert.Alert;
import org.example.control.ControlRequest;
import org.example.device.Device;
import org.example.model.Chamber;
import org.example.model.SensorReading;
import org.example.model.SensorType;
import org.example.rule.Violation;

/**
 * Monitors one chamber for a fixed number of cycles: reads every sensor, records the value,
 * raises alerts for rule violations and queues the responding devices.
 * <p>
 * A {@link Callable} submitted to an executor rather than a {@code Thread} subclass: the task
 * is separated from how it is run, and failures surface through its {@code Future}.
 *
 * @see MonitoringContext
 */
public final class ChamberMonitor implements Callable<Integer> {

    private final Chamber chamber;
    private final int cycles;
    private final MonitoringContext context;

    public ChamberMonitor(Chamber chamber, int cycles, MonitoringContext context) {
        if (cycles < 0) {
            throw new IllegalArgumentException("Cycles must not be negative: " + cycles);
        }
        this.chamber = chamber;
        this.cycles = cycles;
        this.context = context;
    }

    /** @return the number of readings taken */
    @Override
    public Integer call() throws InterruptedException {
        int readings = 0;
        for (int cycle = 0; cycle < cycles; cycle++) {
            for (SensorType type : SensorType.values()) {
                process(new SensorReading(chamber.id(), type, context.sensors().read(chamber, type), Instant.now()));
                readings++;
            }
            context.baselineUsagePerCycle().forEach(usage -> context.meter().record(chamber.id(), usage));
        }
        return readings;
    }

    private void process(SensorReading reading) throws InterruptedException {
        context.readings().publish(reading);
        if (reading.isValid()) {
            chamber.state().record(reading.type(), reading.value());
        }
        for (Violation violation : context.rules().evaluate(reading, chamber.crop())) {
            context.alerts().publish(toAlert(violation, reading.timestamp()));
            for (Device device : violation.responses()) {
                context.controlQueue().submit(new ControlRequest(chamber, device));
            }
        }
    }

    private Alert toAlert(Violation violation, Instant timestamp) {
        return new Alert(chamber.id(), chamber.crop().name(), violation.type(), violation.severity(),
                violation.message(), timestamp);
    }
}
