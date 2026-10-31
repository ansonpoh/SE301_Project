package org.example.crop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.example.model.SensorType;

/**
 * Looks up the rules for a crop by name (Registry pattern).
 *
 * Shared rules (rows with crop "*" in crops.csv) apply to every crop, which is
 * how the legacy global light and nutrient thresholds behave. A crop with no
 * profile only gets the shared rules, as in the legacy code.
 * Read-only after loading, so it is safe to share between threads.
 */
public class CropRegistry {
    private final Map<String, CropProfile> profiles;
    private final List<ThresholdRule> sharedRules;

    public CropRegistry(Map<String, CropProfile> profiles, List<ThresholdRule> sharedRules) {
        this.profiles = Map.copyOf(profiles);
        this.sharedRules = List.copyOf(sharedRules);
    }

    /** Crop-specific rules for this sensor first, then the shared ones. */
    public List<ThresholdRule> rulesFor(String crop, SensorType sensor) {
        List<ThresholdRule> rules = new ArrayList<>();
        CropProfile profile = profiles.get(crop);
        if (profile != null) {
            rules.addAll(profile.rulesFor(sensor));
        }
        for (ThresholdRule rule : sharedRules) {
            if (rule.sensor() == sensor) {
                rules.add(rule);
            }
        }
        return rules;
    }

    public boolean contains(String crop) {
        return profiles.containsKey(crop);
    }
}
