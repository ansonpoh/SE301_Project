package org.example.device;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Looks up a device by the name used in crops.csv (Registry pattern).
 * Filled once at startup, before any thread starts, and only read afterwards.
 */
public class DeviceRegistry {
    private final Map<String, Device> devices = new LinkedHashMap<>();

    public DeviceRegistry register(Device device) {
        devices.put(device.name(), device);
        return this;
    }

    public boolean contains(String name) {
        return devices.containsKey(name);
    }

    public Device get(String name) {
        Device device = devices.get(name);
        if (device == null) {
            throw new IllegalArgumentException("Unknown device: " + name);
        }
        return device;
    }
}
