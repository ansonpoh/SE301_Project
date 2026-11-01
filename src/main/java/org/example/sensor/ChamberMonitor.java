package org.example.sensor;

import org.example.device.RequestInterruptedException;
import org.example.model.ChamberState;
import org.example.model.SensorType;

/**
 * Simulates one chamber for a day: 20 cycles, each reading all five sensors.
 * A Runnable (not a Thread subclass) so an ExecutorService decides how it runs.
 */
public class ChamberMonitor implements Runnable {
    public static final int CYCLES = 20;
    private static final int WATER_PER_CYCLE = 2;
    private static final int ENERGY_PER_CYCLE = 1;

    private final String chamberId;
    private final String crop;
    private final ChamberState state = new ChamberState();
    private final SensorSource sensors;
    private final SensorProcessor processor;
    private final UsageMeter resources;
    /** Written by the chamber thread, read by the main thread after it stops. */
    private volatile int cyclesDone = 0;

    public ChamberMonitor(String chamberId, String crop, SensorSource sensors,
                          SensorProcessor processor, UsageMeter resources) {
        this.chamberId = chamberId;
        this.crop = crop;
        this.sensors = sensors;
        this.processor = processor;
        this.resources = resources;
    }

    public String chamberId() {
        return chamberId;
    }

    /** Cycles fully processed; less than CYCLES if the chamber failed or was stopped early. */
    public int cyclesDone() {
        return cyclesDone;
    }

    /**
     * Stops before its next reading once its thread is interrupted (FarmSimulation's
     * shutdownNow()), or as soon as a device request is cut short by that interrupt
     * (see ControlSink); FarmSimulation waits for it to stop before writing the
     * report, so it raises no alerts after the report.
     */
    @Override
    public void run() {
        for (int cycle = 0; cycle < CYCLES; cycle++) {
            for (SensorType sensor : SensorType.values()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                double value;
                try {
                    value = sensors.read(sensor);
                } catch (RuntimeException e) {
                    // An unresponsive sensor raises a Sensor Failure alert (spec 3.4)
                    // and the chamber carries on with its other sensors.
                    String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    processor.sensorFailure(chamberId, crop, sensor, "no response: " + reason);
                    continue;
                }
                try {
                    processor.process(chamberId, crop, state, sensor, value);
                } catch (RequestInterruptedException e) {
                    return;   // stopped while queueing a device request: a partial day, not a failure
                }
            }
            resources.addWater(chamberId, WATER_PER_CYCLE);
            resources.addEnergy(chamberId, ENERGY_PER_CYCLE);
            cyclesDone = cycle + 1;
        }
    }
}
