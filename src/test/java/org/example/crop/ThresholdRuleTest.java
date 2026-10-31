package org.example.crop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.example.alert.Severity;
import org.example.crop.ThresholdRule.Direction;
import org.example.model.SensorType;
import org.junit.jupiter.api.Test;

class ThresholdRuleTest {
    private static final ThresholdRule TOMATO_HIGH = new ThresholdRule(
            SensorType.TEMPERATURE, Direction.HIGH, 28, "Cooling Fan", 0, 10, Severity.CRITICAL, "{crop} High Temp");
    private static final ThresholdRule TOMATO_LOW = new ThresholdRule(
            SensorType.TEMPERATURE, Direction.LOW, 22, null, 0, 15, Severity.CRITICAL, "{crop} Low Temp");
    private static final ThresholdRule LOW_LIGHT = new ThresholdRule(
            SensorType.LIGHT, Direction.LOW, 500, "Grow Light", 0, 0, Severity.INFO, "Low Light Level");

    @Test
    void comparisonIsStrictLikeTheLegacyCode() {
        assertTrue(TOMATO_HIGH.isBreachedBy(29));
        assertFalse(TOMATO_HIGH.isBreachedBy(28));
        assertTrue(TOMATO_LOW.isBreachedBy(21));
        assertFalse(TOMATO_LOW.isBreachedBy(22));
    }

    @Test
    void alertTextMatchesTheLegacyStrings() {
        assertEquals("CH1 Tomato High Temp", TOMATO_HIGH.alertText("CH1", "Tomato"));
        assertEquals("CH3 Low Light Level", LOW_LIGHT.alertText("CH3", "Lettuce"));
    }

    @Test
    void ruleWithoutDeviceTriggersNothing() {
        assertTrue(TOMATO_HIGH.triggersDevice());
        assertFalse(TOMATO_LOW.triggersDevice());
    }

    @Test
    void registryListsCropRulesBeforeSharedRules() {
        CropRegistry registry = new CropRegistry(
                Map.of("Tomato", new CropProfile("Tomato", List.of(TOMATO_HIGH, TOMATO_LOW))),
                List.of(LOW_LIGHT));

        assertEquals(List.of(TOMATO_HIGH, TOMATO_LOW), registry.rulesFor("Tomato", SensorType.TEMPERATURE));
        assertEquals(List.of(LOW_LIGHT), registry.rulesFor("Tomato", SensorType.LIGHT));
        // A crop with no profile still gets the shared rules, as in the legacy code.
        assertEquals(List.of(LOW_LIGHT), registry.rulesFor("Unknown", SensorType.LIGHT));
        assertEquals(List.of(), registry.rulesFor("Unknown", SensorType.TEMPERATURE));
    }
}
