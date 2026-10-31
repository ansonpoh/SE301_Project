package org.example.alert;

/**
 * An out-of-range or invalid reading, e.g. chamber "CH1", crop "Tomato",
 * message "CH1 Tomato High Temp", severity CRITICAL.
 */
public record Alert(String chamberId, String crop, String message, Severity severity) {
}
