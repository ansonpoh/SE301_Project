package org.example.simulation;

import org.example.model.Chamber;
import org.example.model.SensorType;

/**
 * Where sensor values come from. Monitors depend on this abstraction (DIP), so the random
 * simulator can be swapped for real hardware or a deterministic stub in tests.
 */
@FunctionalInterface
public interface SensorSource {

    double read(Chamber chamber, SensorType type);
}
