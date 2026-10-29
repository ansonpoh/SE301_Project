package org.example.metrics;

/** Consumable resources tracked for the usage reports. */
public enum Resource {
    WATER("Water", "L"),
    ENERGY("Energy", "kWh"),
    NUTRIENT("Nutrient", "ml");

    private final String label;
    private final String unit;

    Resource(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }
}
