package org.example.alert;

import java.time.Instant;
import java.util.Objects;

/** An alert raised for a chamber. Immutable, so it can be shared freely between threads. */
public record Alert(String chamberId, String cropName, AlertType type, Severity severity, String message,
                   Instant timestamp) {

    public Alert {
        Objects.requireNonNull(chamberId, "chamberId");
        Objects.requireNonNull(cropName, "cropName");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    /** e.g. {@code CRITICAL: CH3 Temperature above maximum (31.0C > 21.0C)}. */
    public String describe() {
        return severity + ": " + message;
    }
}
