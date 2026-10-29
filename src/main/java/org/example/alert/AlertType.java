package org.example.alert;

/** Kinds of alert the system raises (spec section 3.4, plus the legacy light alert). */
public enum AlertType {
    TEMPERATURE("Temperature Alert"),
    HUMIDITY("Humidity Alert"),
    LOW_SOIL_MOISTURE("Low Soil Moisture Alert"),
    NUTRIENT_DEFICIENCY("Nutrient Deficiency Alert"),
    LIGHT_LEVEL("Light Level Alert"),
    SENSOR_FAILURE("Sensor Failure Alert");

    private final String label;

    AlertType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
