package org.example.crop;

import java.util.List;

import org.example.model.SensorType;

/** A crop and its threshold rules, in the order they appear in crops.csv. */
public record CropProfile(String name, List<ThresholdRule> rules) {

    public CropProfile {
        rules = List.copyOf(rules);
    }

    public List<ThresholdRule> rulesFor(SensorType sensor) {
        return rules.stream().filter(r -> r.sensor() == sensor).toList();
    }
}
