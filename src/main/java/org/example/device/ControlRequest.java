package org.example.device;

import org.example.model.ChamberState;

/**
 * "Run this device on this chamber" (Command pattern).
 * Created by a chamber thread, executed later by the control thread.
 * Replaces the legacy "CH1|Cooling Fan" string that had to be split again.
 */
public record ControlRequest(String chamberId, ChamberState state, Device device) {

    /** Runs the device and returns its log text. */
    public String execute() {
        return device.apply(state);
    }
}
