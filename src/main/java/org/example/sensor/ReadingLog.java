package org.example.sensor;

/**
 * Where the sensor processor records each reading attempt (Dependency
 * Inversion): it depends on this, not on the file-backed SensorLog in the
 * report package. Called by every chamber thread at the same time.
 */
public interface ReadingLog {
    void record(String message);
}
