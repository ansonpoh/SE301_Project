package org.example.crop;

import java.util.List;

/** A crop and its threshold rules, in the order they appear in crops.csv. */
public record CropProfile(String name, List<ThresholdRule> rules) {

    public CropProfile {
        rules = List.copyOf(rules);
    }
}
