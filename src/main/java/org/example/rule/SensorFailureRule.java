package org.example.rule;

import java.util.List;
import java.util.Optional;

import org.example.alert.AlertType;
import org.example.alert.Severity;
import org.example.model.CropProfile;
import org.example.model.SensorReading;

/** Raises a critical alert when a sensor reports a value no working sensor could produce. */
public final class SensorFailureRule implements SensorRule {

    @Override
    public Optional<Violation> evaluate(SensorReading reading, CropProfile crop) {
        if (reading.isValid()) {
            return Optional.empty();
        }
        String message = reading.chamberId() + " " + reading.type().label()
                + " sensor reported an invalid reading (" + reading.value() + ")";
        return Optional.of(new Violation(AlertType.SENSOR_FAILURE, Severity.CRITICAL, message, List.of()));
    }
}
