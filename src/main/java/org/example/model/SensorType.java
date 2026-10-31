package org.example.model;

/**
 * The five sensors in every chamber. Declaration order is the order a chamber
 * reads them in each cycle (same as the legacy ChamberThread).
 */
public enum SensorType {
    TEMPERATURE("Temperature"),
    HUMIDITY("Humidity"),
    SOIL("Soil"),
    LIGHT("Light"),
    NUTRIENT("Nutrient");

    private final String label;

    SensorType(String label) {
        this.label = label;
    }

    /** Name used in log messages, e.g. "Temperature". */
    public String label() {
        return label;
    }
}
