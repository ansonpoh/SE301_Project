package org.example.crop;

import java.util.List;
import java.util.OptionalDouble;

import org.example.model.SensorType;

/**
 * The rules a crop is checked against (Dependency Inversion): the rule engine
 * and the reports read them through this, not through the CSV-loaded
 * CropRegistry that implements it.
 */
public interface CropRules {

    /** Crop-specific rules for this sensor first, then the shared ones. */
    List<ThresholdRule> rulesFor(String crop, SensorType sensor);

    /**
     * The crop profile's acceptable range for one sensor (spec 3.1), worked out
     * from its rules: the highest LOW limit and the lowest HIGH limit, so a
     * reading inside it breaches no rule. A side with no rule is open.
     */
    default AcceptableRange rangeFor(String crop, SensorType sensor) {
        OptionalDouble min = OptionalDouble.empty();
        OptionalDouble max = OptionalDouble.empty();
        for (ThresholdRule rule : rulesFor(crop, sensor)) {
            double limit = rule.limit();
            if (rule.direction() == ThresholdRule.Direction.LOW) {
                min = OptionalDouble.of(min.isPresent() ? Math.max(min.getAsDouble(), limit) : limit);
            } else {
                max = OptionalDouble.of(max.isPresent() ? Math.min(max.getAsDouble(), limit) : limit);
            }
        }
        return new AcceptableRange(sensor, min, max);
    }
}
