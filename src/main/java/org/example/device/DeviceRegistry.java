package org.example.device;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Looks up a device by the name used in crops.csv (Registry pattern).
 * Filled once at startup, before any thread starts, and only read afterwards.
 */
public class DeviceRegistry {
    private final Map<String, Device> devices = new LinkedHashMap<>();

    /**
     * @throws IllegalStateException if a device with the same name is already
     *         registered: a wiring mistake in SVFRMS, not a config-file error
     */
    public DeviceRegistry register(Device device) {
        if (devices.putIfAbsent(device.name(), device) != null) {
            throw new IllegalStateException("Device registered twice in SVFRMS: " + device.name());
        }
        return this;
    }

    /** @return the device with this name, or empty if none is registered */
    public Optional<Device> find(String name) {
        return Optional.ofNullable(devices.get(name));
    }

    /** @throws IllegalArgumentException if no device has this name */
    public Device get(String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException("Unknown device: " + name));
    }
}
