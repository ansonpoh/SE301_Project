package org.example.alert;

/** An out-of-range reading, e.g. chamber "CH1", crop "Tomato", message "CH1 Tomato High Temp". */
public record Alert(String chamberId, String crop, String message) {
}
