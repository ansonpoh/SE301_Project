package org.example.crop;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.example.model.SensorType;

/**
 * Looks up the rules for a crop by name (Registry pattern).
 *
 * Shared rules (rows with crop "*" in crops.csv) apply to every crop, which is
 * how the legacy global light and nutrient thresholds behave. A crop with no
 * profile only gets the shared rules, as in the legacy code;
 * FarmConfigLoader.loadChambers warns about such a chamber at start-up.
 * Read-only after loading, so it is safe to share between threads.
 */
public class CropRegistry {
    /** Rules per crop and sensor, worked out once here instead of on every reading. */
    private final Map<String, Map<SensorType, List<ThresholdRule>>> rulesByCrop = new HashMap<>();
    private final Map<SensorType, List<ThresholdRule>> sharedOnly;

    public CropRegistry(Map<String, CropProfile> profiles, List<ThresholdRule> sharedRules) {
        profiles.forEach((crop, profile) -> rulesByCrop.put(crop, bySensor(profile.rules(), sharedRules)));
        this.sharedOnly = bySensor(List.of(), sharedRules);
    }

    /** Crop-specific rules for this sensor first, then the shared ones. */
    public List<ThresholdRule> rulesFor(String crop, SensorType sensor) {
        return rulesByCrop.getOrDefault(crop, sharedOnly).get(sensor);
    }

    public boolean contains(String crop) {
        return rulesByCrop.containsKey(crop);
    }

    private static Map<SensorType, List<ThresholdRule>> bySensor(List<ThresholdRule> own, List<ThresholdRule> shared) {
        Map<SensorType, List<ThresholdRule>> rules = new EnumMap<>(SensorType.class);
        for (SensorType sensor : SensorType.values()) {
            rules.put(sensor, Stream.concat(own.stream(), shared.stream()).filter(r -> r.sensor() == sensor).toList());
        }
        return rules;
    }
}
