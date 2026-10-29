package org.example.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.example.TestFixtures;
import org.example.alert.AlertType;
import org.example.alert.Severity;
import org.example.device.StandardDevices;
import org.example.model.CropProfile;
import org.example.model.SensorReading;
import org.example.model.SensorType;
import org.junit.jupiter.api.Test;

class StandardRulesTest {

    private final RuleEngine rules = StandardRules.create();
    private final CropProfile tomato = TestFixtures.tomato();

    private List<Violation> evaluate(SensorType type, double value) {
        return rules.evaluate(new SensorReading("CH1", type, value, Instant.now()), tomato);
    }

    @Test
    void valueInsideRangeRaisesNothing() {
        assertTrue(evaluate(SensorType.TEMPERATURE, 25).isEmpty());
    }

    @Test
    void boundaryValuesRaiseNothing() {
        assertTrue(evaluate(SensorType.TEMPERATURE, 22).isEmpty());
        assertTrue(evaluate(SensorType.TEMPERATURE, 28).isEmpty());
    }

    @Test
    void highTemperatureActivatesCoolingFan() {
        Violation violation = evaluate(SensorType.TEMPERATURE, 31).getFirst();

        assertEquals(AlertType.TEMPERATURE, violation.type());
        assertEquals(Severity.CRITICAL, violation.severity());
        assertEquals(List.of(StandardDevices.COOLING_FAN), violation.responses());
        assertEquals("CH1 Temperature above maximum (31.0C > 28.0C)", violation.message());
    }

    @Test
    void lowTemperatureActivatesHeatingUnit() {
        assertEquals(List.of(StandardDevices.HEATING_UNIT), evaluate(SensorType.TEMPERATURE, 15).getFirst().responses());
    }

    @Test
    void severityGrowsWithDeviation() {
        // Tomato temperature span is 6C: <0.6C over is INFO, <3C is WARNING, otherwise CRITICAL.
        assertEquals(Severity.INFO, evaluate(SensorType.TEMPERATURE, 28.5).getFirst().severity());
        assertEquals(Severity.WARNING, evaluate(SensorType.TEMPERATURE, 29).getFirst().severity());
        assertEquals(Severity.CRITICAL, evaluate(SensorType.TEMPERATURE, 31).getFirst().severity());
    }

    @Test
    void lowSoilMoistureActivatesWaterPumpButHighDoesNot() {
        Violation low = evaluate(SensorType.SOIL_MOISTURE, 30).getFirst();
        assertEquals(AlertType.LOW_SOIL_MOISTURE, low.type());
        assertEquals(List.of(StandardDevices.WATER_PUMP), low.responses());

        assertTrue(evaluate(SensorType.SOIL_MOISTURE, 70).isEmpty());
    }

    @Test
    void highHumidityActivatesDehumidifierAndVentilation() {
        assertEquals(List.of(StandardDevices.DEHUMIDIFIER, StandardDevices.VENTILATION_SYSTEM),
                evaluate(SensorType.HUMIDITY, 95).getFirst().responses());
    }

    @Test
    void invalidReadingRaisesOnlySensorFailure() {
        List<Violation> violations = evaluate(SensorType.TEMPERATURE, Double.NaN);

        assertEquals(1, violations.size());
        assertEquals(AlertType.SENSOR_FAILURE, violations.getFirst().type());
        assertEquals(Severity.CRITICAL, violations.getFirst().severity());
        assertTrue(violations.getFirst().responses().isEmpty());
    }
}
