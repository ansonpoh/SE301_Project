package org.example.crop;

import java.util.OptionalDouble;

import org.example.model.SensorType;

/**
 * One attribute of a crop profile (spec 3.1), e.g. "Temperature 22-28 C".
 * Readings outside it raise an alert; an empty side has no limit.
 */
public record AcceptableRange(SensorType sensor, OptionalDouble min, OptionalDouble max) {

    /** e.g. "Temperature 22-28 C", "Soil >= 45 %", "Humidity not monitored". */
    public String describe() {
        String unit = " " + sensor.unit();
        if (min.isPresent() && max.isPresent()) {
            return sensor.label() + " " + number(min.getAsDouble()) + "-" + number(max.getAsDouble()) + unit;
        }
        if (min.isPresent()) {
            return sensor.label() + " >= " + number(min.getAsDouble()) + unit;
        }
        if (max.isPresent()) {
            return sensor.label() + " <= " + number(max.getAsDouble()) + unit;
        }
        return sensor.label() + " not monitored";
    }

    /** 28.0 -> "28", 27.5 -> "27.5". */
    private static String number(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e15 ? Long.toString((long) value) : Double.toString(value);
    }
}
