package org.example.sensor;

import org.example.alert.Alert;
import org.example.alert.AlertListener;
import org.example.alert.Severity;
import org.example.crop.CropRegistry;
import org.example.crop.ThresholdRule;
import org.example.device.ControlRequest;
import org.example.device.ControlSink;
import org.example.device.DeviceRegistry;
import org.example.model.ChamberState;
import org.example.model.SensorType;
import org.example.report.UsageMeter;
import org.example.report.SensorLog;

/**
 * Handles one sensor reading. Replaces the legacy processSensor(): instead of
 * an if/else chain per crop, it checks whatever rules the CropRegistry holds
 * for this crop and sensor, so it never needs to change when crops change.
 *
 * Holds no lock while publishing alerts or queueing device requests, so it can
 * never wait for one lock while holding another (no deadlock possible).
 *
 * Alerts, usage and device requests go through interfaces (AlertListener,
 * UsageMeter, ControlSink), so the rule engine does not depend on how they are
 * implemented (Dependency Inversion).
 */
public class SensorProcessor {
    private final CropRegistry crops;
    private final DeviceRegistry devices;
    private final AlertListener alerts;
    private final UsageMeter resources;
    private final ControlSink control;
    private final SensorLog sensorLog;
    private final SensorRanges ranges;

    public SensorProcessor(CropRegistry crops, DeviceRegistry devices, AlertListener alerts,
                           UsageMeter resources, ControlSink control, SensorLog sensorLog,
                           SensorRanges ranges) {
        this.crops = crops;
        this.devices = devices;
        this.alerts = alerts;
        this.resources = resources;
        this.control = control;
        this.sensorLog = sensorLog;
        this.ranges = ranges;
    }

    public void process(String chamberId, String crop, ChamberState state, SensorType sensor, double value) {
        sensorLog.record("Processing " + crop + " " + sensor.label() + " = " + value);
        if (!ranges.isValid(sensor, value)) {
            // The invalid value is not stored and no threshold rule runs on it,
            // so no device reacts to a broken sensor. Already logged above.
            publishFailure(chamberId, crop, sensor, "invalid reading " + value);
            return;
        }
        state.set(sensor, value);

        for (ThresholdRule rule : crops.rulesFor(crop, sensor)) {
            if (!rule.isBreachedBy(value)) {
                continue;
            }
            alerts.onAlert(new Alert(chamberId, crop, rule.alertText(chamberId, crop), rule.severity()));
            resources.addWater(rule.waterCost());
            resources.addEnergy(rule.energyCost());
            if (rule.triggersDevice()) {
                control.request(new ControlRequest(chamberId, state, devices.get(rule.deviceName())));
            }
        }
    }

    /**
     * Spec 3.4 Sensor Failure Alert for a sensor that did not respond. Like
     * process(), it writes one sensor-log entry per reading attempt.
     */
    public void sensorFailure(String chamberId, String crop, SensorType sensor, String detail) {
        sensorLog.record("Sensor failure " + crop + " " + sensor.label() + ": " + detail);
        publishFailure(chamberId, crop, sensor, detail);
    }

    /** CRITICAL, no costs, no device. */
    private void publishFailure(String chamberId, String crop, SensorType sensor, String detail) {
        alerts.onAlert(new Alert(chamberId, crop,
                chamberId + " " + crop + " " + sensor.label() + " Sensor Failure (" + detail + ")",
                Severity.CRITICAL));
    }
}
