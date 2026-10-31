package org.example.crop;

import org.example.alert.Severity;
import org.example.model.SensorType;

/**
 * One row of crops.csv: "if SENSOR is above/below LIMIT, raise MESSAGE,
 * with SEVERITY, charge the costs and (optionally) start DEVICE".
 * Replaces one copy of the legacy TRACK_ALERT + CREATE_ALERT block.
 *
 * @param deviceName device to request, or null if the rule triggers no device
 * @param message    alert text after the chamber id; "{crop}" is replaced by the crop name
 */
public record ThresholdRule(SensorType sensor, Direction direction, double limit,
                            String deviceName, int waterCost, int energyCost, Severity severity,
                            String message) {

    public enum Direction { HIGH, LOW }

    /** Strict comparison, exactly like the legacy code (value > 28, value < 22). */
    public boolean isBreachedBy(double value) {
        return direction == Direction.HIGH ? value > limit : value < limit;
    }

    public boolean triggersDevice() {
        return deviceName != null;
    }

    /** e.g. "CH1 Tomato High Temp" or "CH3 Low Light Level". */
    public String alertText(String chamberId, String crop) {
        return chamberId + " " + message.replace("{crop}", crop);
    }
}
