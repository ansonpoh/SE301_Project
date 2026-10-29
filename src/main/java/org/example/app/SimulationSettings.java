package org.example.app;

import java.util.List;

import org.example.metrics.ResourceUsage;

/**
 * Tunable parameters of a monitoring run. Immutable.
 *
 * @param cycles                number of sensor sweeps each chamber performs
 * @param controlQueueCapacity  maximum pending device requests before producers block
 * @param baselineUsagePerCycle resources each chamber consumes per cycle regardless of devices
 */
public record SimulationSettings(int cycles, int controlQueueCapacity, List<ResourceUsage> baselineUsagePerCycle) {

    public SimulationSettings {
        if (cycles < 0) {
            throw new IllegalArgumentException("Cycles must not be negative: " + cycles);
        }
        if (controlQueueCapacity < 1) {
            throw new IllegalArgumentException("Control queue capacity must be positive: " + controlQueueCapacity);
        }
        baselineUsagePerCycle = List.copyOf(baselineUsagePerCycle);
    }

    /** Values used by the legacy system: 20 cycles, a queue of 5, 2 L water and 1 kWh per cycle. */
    public static SimulationSettings defaults() {
        return new SimulationSettings(20, 5, List.of(ResourceUsage.water(2), ResourceUsage.energy(1)));
    }
}
