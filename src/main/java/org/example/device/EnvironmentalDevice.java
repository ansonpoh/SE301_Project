package org.example.device;

import java.util.List;
import java.util.Objects;

import org.example.metrics.ResourceUsage;
import org.example.model.ChamberState;

/**
 * A device whose behaviour is fully described by its effects and resource cost.
 * Composition over inheritance: devices differ in configuration, not in subclasses.
 */
public record EnvironmentalDevice(String name, List<Effect> effects, List<ResourceUsage> usagePerActivation)
        implements Device {

    public EnvironmentalDevice {
        Objects.requireNonNull(name, "name");
        if (effects.isEmpty()) {
            throw new IllegalArgumentException("Device " + name + " must have at least one effect");
        }
        effects = List.copyOf(effects);
        usagePerActivation = List.copyOf(usagePerActivation);
    }

    @Override
    public List<String> operate(ChamberState state) {
        return effects.stream()
                .map(effect -> effect.applyTo(state))
                .toList();
    }
}
