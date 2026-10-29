package org.example.metrics;

import java.util.Objects;

/** An amount of a resource consumed by one action. Immutable. */
public record ResourceUsage(Resource resource, long amount) {

    public ResourceUsage {
        Objects.requireNonNull(resource, "resource");
        if (amount < 0) {
            throw new IllegalArgumentException("Usage amount must not be negative: " + amount);
        }
    }

    public static ResourceUsage water(long litres) {
        return new ResourceUsage(Resource.WATER, litres);
    }

    public static ResourceUsage energy(long kilowattHours) {
        return new ResourceUsage(Resource.ENERGY, kilowattHours);
    }

    public static ResourceUsage nutrient(long millilitres) {
        return new ResourceUsage(Resource.NUTRIENT, millilitres);
    }
}
