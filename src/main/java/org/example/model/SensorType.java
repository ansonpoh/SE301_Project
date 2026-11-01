package org.example.model;

/**
 * The five sensors in every chamber. Declaration order is the order a chamber
 * reads them in each cycle (same as the legacy ChamberThread).
 */
public enum SensorType {
    TEMPERATURE("Temperature", "C", 24),
    HUMIDITY("Humidity", "%", 70),
    SOIL("Soil", "%", 60),
    LIGHT("Light", "lux", 600),
    NUTRIENT("Nutrient", "%", 70);

    private final String label;
    private final String unit;
    private final double initialValue;

    SensorType(String label, String unit, double initialValue) {
        this.label = label;
        this.unit = unit;
        this.initialValue = initialValue;
    }

    /** Unit of its readings, e.g. "C" or "lux" (spec 3.1). */
    public String unit() {
        return unit;
    }

    /** A chamber's reading before its first cycle (the legacy ChamberState defaults). */
    public double initialValue() {
        return initialValue;
    }

    /** Name used in log messages, e.g. "Temperature". */
    public String label() {
        return label;
    }
}
