package org.example.rule;

import org.example.alert.Severity;
import org.example.model.Range;

/** Decides how severe an out-of-range value is. */
@FunctionalInterface
public interface SeverityPolicy {

    Severity classify(double value, Range range);

    /**
     * Severity grows with the deviation relative to the width of the acceptable range:
     * below {@code warningRatio} is INFO, below {@code criticalRatio} is WARNING, otherwise CRITICAL.
     */
    static SeverityPolicy proportional(double warningRatio, double criticalRatio) {
        if (warningRatio < 0 || warningRatio > criticalRatio) {
            throw new IllegalArgumentException("Require 0 <= warningRatio <= criticalRatio");
        }
        return (value, range) -> {
            double ratio = range.span() == 0 ? Double.POSITIVE_INFINITY : range.deviation(value) / range.span();
            if (ratio >= criticalRatio) {
                return Severity.CRITICAL;
            }
            return ratio >= warningRatio ? Severity.WARNING : Severity.INFO;
        };
    }
}
