package org.example.sensor;

import org.example.alert.Alert;
import org.example.alert.AlertPublisher;
import org.example.crop.CropRegistry;
import org.example.crop.ThresholdRule;
import org.example.device.ControlRequest;
import org.example.device.ControlService;
import org.example.device.DeviceRegistry;
import org.example.model.ChamberState;
import org.example.model.SensorType;
import org.example.report.ResourceMeter;
import org.example.report.SensorLog;

/**
 * Handles one sensor reading. Replaces the legacy processSensor(): instead of
 * an if/else chain per crop, it checks whatever rules the CropRegistry holds
 * for this crop and sensor, so it never needs to change when crops change.
 *
 * Holds no lock while publishing alerts or queueing device requests, so it can
 * never wait for one lock while holding another (no deadlock possible).
 */
public class SensorProcessor {
    private final CropRegistry crops;
    private final DeviceRegistry devices;
    private final AlertPublisher alerts;
    private final ResourceMeter resources;
    private final ControlService control;
    private final SensorLog sensorLog;

    public SensorProcessor(CropRegistry crops, DeviceRegistry devices, AlertPublisher alerts,
                           ResourceMeter resources, ControlService control, SensorLog sensorLog) {
        this.crops = crops;
        this.devices = devices;
        this.alerts = alerts;
        this.resources = resources;
        this.control = control;
        this.sensorLog = sensorLog;
    }

    public void process(String chamberId, String crop, ChamberState state, SensorType sensor, double value) {
        sensorLog.record("Processing " + crop + " " + sensor.label() + " = " + value);
        state.set(sensor, value);

        for (ThresholdRule rule : crops.rulesFor(crop, sensor)) {
            if (!rule.isBreachedBy(value)) {
                continue;
            }
            alerts.publish(new Alert(chamberId, crop, rule.alertText(chamberId, crop)));
            resources.addWater(rule.waterCost());
            resources.addEnergy(rule.energyCost());
            if (rule.triggersDevice()) {
                control.request(new ControlRequest(chamberId, state, devices.get(rule.deviceName())));
            }
        }
    }
}
