package org.example.device;

import org.example.model.SensorType;

/**
 * Which reading a device moves, and which way, e.g. the Cooling Fan lowers
 * TEMPERATURE. Lets crops.csv be checked at start-up (a rule's device must
 * correct the breach that triggers it) and lets reports find devices by what
 * they do instead of by class or name.
 */
public record Effect(SensorType sensor, Change change) {

    public enum Change { RAISES, LOWERS }

    public static Effect raises(SensorType sensor) {
        return new Effect(sensor, Change.RAISES);
    }

    public static Effect lowers(SensorType sensor) {
        return new Effect(sensor, Change.LOWERS);
    }

    /** e.g. "lowers TEMPERATURE" */
    @Override
    public String toString() {
        return change.name().toLowerCase() + " " + sensor;
    }
}
