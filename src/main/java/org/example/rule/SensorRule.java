package org.example.rule;

import java.util.Optional;

import org.example.model.CropProfile;
import org.example.model.SensorReading;

/**
 * Checks one reading against a crop's requirements (Strategy).
 * New checks are added as new implementations, without touching existing rules.
 */
@FunctionalInterface
public interface SensorRule {

    Optional<Violation> evaluate(SensorReading reading, CropProfile crop);
}
