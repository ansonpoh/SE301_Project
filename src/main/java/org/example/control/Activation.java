package org.example.control;

import java.util.List;
import java.util.Objects;

/** Record of a device having run in a chamber and what it changed. Immutable. */
public record Activation(String chamberId, String deviceName, List<String> changes) {

    public Activation {
        Objects.requireNonNull(chamberId, "chamberId");
        Objects.requireNonNull(deviceName, "deviceName");
        changes = List.copyOf(changes);
    }

    public String summary() {
        return deviceName + " Activated";
    }
}
