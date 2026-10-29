package org.example.rule;

import java.util.List;
import java.util.Objects;

import org.example.alert.AlertType;
import org.example.alert.Severity;
import org.example.device.Device;

/**
 * Outcome of a rule that found a problem: what to report and which devices should respond.
 */
public record Violation(AlertType type, Severity severity, String message, List<Device> responses) {

    public Violation {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        responses = List.copyOf(responses);
    }
}
