package org.example.sensor;

import java.util.Random;

import org.example.model.SensorType;

/**
 * Random readings in the same ranges as the legacy ChamberThread.
 * java.util.Random is thread-safe, so one instance can serve every chamber.
 * Pass a seeded Random to get repeatable values.
 */
public class RandomSensorSource implements SensorSource {
    private final Random random;

    public RandomSensorSource(Random random) {
        this.random = random;
    }

    @Override
    public double read(SensorType type) {
        return switch (type) {
            case TEMPERATURE -> 10 + random.nextInt(30);
            case HUMIDITY -> 40 + random.nextInt(60);
            case SOIL -> 20 + random.nextInt(60);
            case LIGHT -> 300 + random.nextInt(900);
            case NUTRIENT -> 20 + random.nextInt(80);
        };
    }
}
