package org.example.rule;

import java.util.List;
import java.util.Optional;

import org.example.model.CropProfile;
import org.example.model.SensorReading;

/** Runs every registered rule against a reading. Stateless, therefore thread-safe. */
public final class RuleEngine {

    private final List<SensorRule> rules;

    public RuleEngine(List<SensorRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<Violation> evaluate(SensorReading reading, CropProfile crop) {
        return rules.stream()
                .map(rule -> rule.evaluate(reading, crop))
                .flatMap(Optional::stream)
                .toList();
    }
}
