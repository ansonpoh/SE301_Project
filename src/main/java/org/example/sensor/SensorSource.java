package org.example.sensor;

import org.example.model.SensorType;

/** Where readings come from. The simulation uses random values; real sensors could plug in here. */
public interface SensorSource {
    double read(SensorType type);
}
