package org.example.simulation;

import java.util.List;
import java.util.Objects;

import org.example.alert.Alert;
import org.example.control.ControlQueue;
import org.example.event.EventBus;
import org.example.metrics.ResourceMeter;
import org.example.metrics.ResourceUsage;
import org.example.model.SensorReading;
import org.example.rule.RuleEngine;

/**
 * Collaborators shared by every {@link ChamberMonitor} (parameter object).
 *
 * @param baselineUsagePerCycle resources a chamber consumes every cycle regardless of devices
 */
public record MonitoringContext(SensorSource sensors, RuleEngine rules, EventBus<SensorReading> readings,
                                EventBus<Alert> alerts, ControlQueue controlQueue, ResourceMeter meter,
                                List<ResourceUsage> baselineUsagePerCycle) {

    public MonitoringContext {
        Objects.requireNonNull(sensors, "sensors");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(readings, "readings");
        Objects.requireNonNull(alerts, "alerts");
        Objects.requireNonNull(controlQueue, "controlQueue");
        Objects.requireNonNull(meter, "meter");
        baselineUsagePerCycle = List.copyOf(baselineUsagePerCycle);
    }
}
