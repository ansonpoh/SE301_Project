package org.example.rule;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.example.alert.AlertType;
import org.example.device.Device;
import org.example.model.CropProfile;
import org.example.model.Range;
import org.example.model.SensorReading;
import org.example.model.SensorType;

/**
 * Compares a sensor value with the crop's range and reacts to values below and/or above it.
 * <p>
 * One generic rule replaces the legacy per-crop if/else blocks: the thresholds come from the
 * {@link CropProfile}, so the rule never mentions a crop by name.
 */
public final class ThresholdRule implements SensorRule {

    /** How the system reacts to one side of the range being breached. */
    public record Response(AlertType alertType, List<Device> devices) {

        public Response {
            Objects.requireNonNull(alertType, "alertType");
            devices = List.copyOf(devices);
        }
    }

    private final SensorType sensor;
    private final Optional<Response> whenLow;
    private final Optional<Response> whenHigh;
    private final SeverityPolicy severityPolicy;

    private ThresholdRule(SensorType sensor, Optional<Response> whenLow, Optional<Response> whenHigh,
                          SeverityPolicy severityPolicy) {
        this.sensor = Objects.requireNonNull(sensor, "sensor");
        this.whenLow = whenLow;
        this.whenHigh = whenHigh;
        this.severityPolicy = Objects.requireNonNull(severityPolicy, "severityPolicy");
    }

    public static ThresholdRule bothSides(SensorType sensor, Response whenLow, Response whenHigh,
                                          SeverityPolicy severityPolicy) {
        return new ThresholdRule(sensor, Optional.of(whenLow), Optional.of(whenHigh), severityPolicy);
    }

    public static ThresholdRule lowOnly(SensorType sensor, Response whenLow, SeverityPolicy severityPolicy) {
        return new ThresholdRule(sensor, Optional.of(whenLow), Optional.empty(), severityPolicy);
    }

    @Override
    public Optional<Violation> evaluate(SensorReading reading, CropProfile crop) {
        if (reading.type() != sensor || !reading.isValid()) {
            return Optional.empty();
        }
        Range range = crop.range(sensor);
        double value = reading.value();
        if (range.isBelow(value)) {
            return whenLow.map(response -> violation(response, reading, range, "below minimum", "<", range.min()));
        }
        if (range.isAbove(value)) {
            return whenHigh.map(response -> violation(response, reading, range, "above maximum", ">", range.max()));
        }
        return Optional.empty();
    }

    private Violation violation(Response response, SensorReading reading, Range range,
                                String breach, String comparator, double threshold) {
        String message = String.format("%s %s %s (%s %s %s)",
                reading.chamberId(), sensor.label(), breach,
                sensor.format(reading.value()), comparator, sensor.format(threshold));
        return new Violation(response.alertType(), severityPolicy.classify(reading.value(), range),
                message, response.devices());
    }
}
