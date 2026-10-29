package org.example.control;

import java.util.Objects;

import org.example.device.Device;
import org.example.model.Chamber;

/**
 * Request to activate a device in a chamber (Command): a typed object instead of the legacy
 * {@code "CH1|Cooling Fan"} string that had to be split and re-parsed.
 */
public record ControlRequest(Chamber chamber, Device device) {

    public ControlRequest {
        Objects.requireNonNull(chamber, "chamber");
        Objects.requireNonNull(device, "device");
    }
}
