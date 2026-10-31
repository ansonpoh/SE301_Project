package org.example.sensor;

import org.example.model.ChamberState;
import org.example.model.SensorType;
import org.example.report.UsageMeter;

/**
 * Simulates one chamber for a day: 20 cycles, each reading all five sensors.
 * A Runnable (not a Thread subclass) so an ExecutorService decides how it runs.
 */
public class ChamberMonitor implements Runnable {
    private static final int CYCLES = 20;
    private static final int WATER_PER_CYCLE = 2;
    private static final int ENERGY_PER_CYCLE = 1;

    private final String chamberId;
    private final String crop;
    private final ChamberState state = new ChamberState();
    private final SensorSource sensors;
    private final SensorProcessor processor;
    private final UsageMeter resources;

    public ChamberMonitor(String chamberId, String crop, SensorSource sensors,
                          SensorProcessor processor, UsageMeter resources) {
        this.chamberId = chamberId;
        this.crop = crop;
        this.sensors = sensors;
        this.processor = processor;
        this.resources = resources;
    }

    @Override
    public void run() {
        for (int cycle = 0; cycle < CYCLES; cycle++) {
            for (SensorType sensor : SensorType.values()) {
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
                processor.process(chamberId, crop, state, sensor, value);
            }
            resources.addWater(WATER_PER_CYCLE);
            resources.addEnergy(ENERGY_PER_CYCLE);
        }
    }
}
