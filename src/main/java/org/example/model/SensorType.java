package org.example.model;

/**
 * Sensor kinds installed in every growing chamber.
 * Each type knows its display label, unit and the physically plausible limits used to
 * detect a failed sensor.
 */
public enum SensorType {
    TEMPERATURE("Temperature", "C", new Range(-40, 80)),
    HUMIDITY("Humidity", "%", new Range(0, 100)),
    SOIL_MOISTURE("Soil Moisture", "%", new Range(0, 100)),
    LIGHT("Light Level", " lux", new Range(0, 200_000)),
    NUTRIENT("Nutrient Level", "%", new Range(0, 100));

    private final String label;
    private final String unit;
    private final Range physicalLimits;

    SensorType(String label, String unit, Range physicalLimits) {
        this.label = label;
        this.unit = unit;
        this.physicalLimits = physicalLimits;
    }

    public String label() {
        return label;
    }

    /** True when the value could have come from a working sensor. */
    public boolean isPlausible(double value) {
        return !Double.isNaN(value) && physicalLimits.contains(value);
    }

    public String format(double value) {
        return String.format("%.1f%s", value, unit);
    }
}
