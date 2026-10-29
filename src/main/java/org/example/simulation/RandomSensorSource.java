package org.example.simulation;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.example.model.Chamber;
import org.example.model.SensorType;

/**
 * Simulated sensors producing whole-number values in the same ranges as the legacy system,
 * with an optional probability of a failed (NaN) reading.
 * <p>
 * Uses {@link ThreadLocalRandom} rather than one shared {@code Random}, so chamber threads do
 * not contend on a single generator.
 */
public final class RandomSensorSource implements SensorSource {

    private record Bounds(int lowest, int width) {
    }

    private static final Map<SensorType, Bounds> BOUNDS = new EnumMap<>(Map.of(
            SensorType.TEMPERATURE, new Bounds(10, 30),
            SensorType.HUMIDITY, new Bounds(40, 60),
            SensorType.SOIL_MOISTURE, new Bounds(20, 60),
            SensorType.LIGHT, new Bounds(300, 900),
            SensorType.NUTRIENT, new Bounds(20, 80)));

    private final double failureRate;

    public RandomSensorSource(double failureRate) {
        if (failureRate < 0 || failureRate > 1) {
            throw new IllegalArgumentException("Failure rate must be between 0 and 1: " + failureRate);
        }
        this.failureRate = failureRate;
    }

    @Override
    public double read(Chamber chamber, SensorType type) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() < failureRate) {
            return Double.NaN;
        }
        Bounds bounds = BOUNDS.get(type);
        return bounds.lowest() + random.nextInt(bounds.width());
    }
}
