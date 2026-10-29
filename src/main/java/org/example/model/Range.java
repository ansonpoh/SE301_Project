package org.example.model;

/**
 * Inclusive range of acceptable values for one sensor. Immutable.
 */
public record Range(double min, double max) {

    public Range {
        if (min > max) {
            throw new IllegalArgumentException("Range min " + min + " is greater than max " + max);
        }
    }

    public boolean isBelow(double value) {
        return value < min;
    }

    public boolean isAbove(double value) {
        return value > max;
    }

    public boolean contains(double value) {
        return !isBelow(value) && !isAbove(value);
    }

    public double span() {
        return max - min;
    }

    /** How far the value lies outside the range; 0 when inside. */
    public double deviation(double value) {
        if (isBelow(value)) {
            return min - value;
        }
        if (isAbove(value)) {
            return value - max;
        }
        return 0;
    }
}
