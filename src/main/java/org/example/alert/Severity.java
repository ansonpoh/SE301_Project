package org.example.alert;

/** Alert severity levels from spec 3.4. Set per rule in crops.csv. */
public enum Severity {
    /** Informational; no immediate action needed. */
    INFO,
    /** May need attention to prevent problems. */
    WARNING,
    /** Needs immediate action to protect crops or the system. */
    CRITICAL
}
