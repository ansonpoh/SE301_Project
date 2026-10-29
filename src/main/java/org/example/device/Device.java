package org.example.device;

import java.util.List;

import org.example.metrics.ResourceUsage;
import org.example.model.ChamberState;

/**
 * An automated device that can be activated in a chamber (Strategy).
 * New devices are added by implementing this interface; callers never switch on device names.
 */
public interface Device {

    String name();

    /**
     * Applies the device's effect to the chamber.
     *
     * @return human-readable descriptions of what changed
     */
    List<String> operate(ChamberState state);

    /** Resources consumed each time the device is activated. */
    List<ResourceUsage> usagePerActivation();
}
