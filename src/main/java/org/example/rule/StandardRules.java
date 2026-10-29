package org.example.rule;

import java.util.List;

import org.example.alert.AlertType;
import org.example.device.StandardDevices;
import org.example.model.SensorType;
import org.example.rule.ThresholdRule.Response;

/**
 * The facility's monitoring rules: which alert to raise and which devices to activate when a
 * reading leaves its crop range (spec sections 3.3 and 3.4). This is the single place to change
 * when a reaction changes.
 */
public final class StandardRules {

    /** Under 10% of the range width outside is INFO, 50% or more is CRITICAL. */
    public static final SeverityPolicy DEFAULT_SEVERITY = SeverityPolicy.proportional(0.1, 0.5);

    private StandardRules() {
    }

    public static RuleEngine create() {
        return create(DEFAULT_SEVERITY);
    }

    public static RuleEngine create(SeverityPolicy severity) {
        return new RuleEngine(List.of(
                new SensorFailureRule(),
                ThresholdRule.bothSides(SensorType.TEMPERATURE,
                        new Response(AlertType.TEMPERATURE, List.of(StandardDevices.HEATING_UNIT)),
                        new Response(AlertType.TEMPERATURE, List.of(StandardDevices.COOLING_FAN)),
                        severity),
                ThresholdRule.bothSides(SensorType.HUMIDITY,
                        new Response(AlertType.HUMIDITY, List.of(StandardDevices.HUMIDIFIER)),
                        new Response(AlertType.HUMIDITY,
                                List.of(StandardDevices.DEHUMIDIFIER, StandardDevices.VENTILATION_SYSTEM)),
                        severity),
                ThresholdRule.lowOnly(SensorType.SOIL_MOISTURE,
                        new Response(AlertType.LOW_SOIL_MOISTURE, List.of(StandardDevices.WATER_PUMP)),
                        severity),
                ThresholdRule.bothSides(SensorType.LIGHT,
                        new Response(AlertType.LIGHT_LEVEL, List.of(StandardDevices.GROW_LIGHT)),
                        new Response(AlertType.LIGHT_LEVEL, List.of(StandardDevices.SHADE_CONTROLLER)),
                        severity),
                ThresholdRule.lowOnly(SensorType.NUTRIENT,
                        new Response(AlertType.NUTRIENT_DEFICIENCY, List.of(StandardDevices.NUTRIENT_INJECTOR)),
                        severity)));
    }
}
