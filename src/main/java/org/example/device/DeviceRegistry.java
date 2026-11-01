package org.example.device;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Looks up a device by the name used in crops.csv (Registry pattern).
 * Filled once at startup, before any thread starts, and only read afterwards.
 */
public class DeviceRegistry {
    private final Map<String, Device> devices = new LinkedHashMap<>();

    /**
     * Every device listed in META-INF/services/org.example.device.Device, found
     * with ServiceLoader. Adding a device is one new class plus one line in that
     * file; no existing class changes (OCP).
     *
     * @throws IllegalStateException if no device is listed, a listed class cannot be
     *         loaded or created, or two share a name: a broken build, not a config-file error
     */
    public static DeviceRegistry discover() {
        return discover(Device.class.getClassLoader());
    }

    /** discover() with the class loader that finds the services files; for tests. */
    static DeviceRegistry discover(ClassLoader loader) {
        DeviceRegistry registry = new DeviceRegistry();
        try {
            ServiceLoader.load(Device.class, loader).forEach(registry::register);
        } catch (ServiceConfigurationError e) {
            // A typo in the services file, or a device without a public no-argument constructor.
            throw new IllegalStateException("Bad device list in META-INF/services/" + Device.class.getName()
                    + ": " + e.getMessage(), e);
        }
        if (registry.devices.isEmpty()) {
            throw new IllegalStateException("No devices listed in META-INF/services/" + Device.class.getName());
        }
        return registry;
    }

    /**
     * @throws IllegalStateException if a device with the same name is already
     *         registered, or the device declares no effects (so crops.csv could
     *         not be checked against it): a wiring mistake, not a config-file error
     */
    public DeviceRegistry register(Device device) {
        if (device.effects() == null || device.effects().isEmpty()) {
            throw new IllegalStateException("Device declares no effects: " + device.name());
        }
        if (devices.putIfAbsent(device.name(), device) != null) {
            throw new IllegalStateException("Device registered twice: " + device.name());
        }
        return this;
    }

    /** @return the device with this name, or empty if none is registered */
    public Optional<Device> find(String name) {
        return Optional.ofNullable(devices.get(name));
    }

    /**
     * Names of the devices with this effect, in registration order, e.g. every
     * device that raises NUTRIENT for the nutrient consumption report.
     */
    public List<String> namesWith(Effect effect) {
        return devices.values().stream().filter(d -> d.effects().contains(effect)).map(Device::name).toList();
    }

    /** @throws IllegalArgumentException if no device has this name */
    public Device get(String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException("Unknown device: " + name));
    }
}
