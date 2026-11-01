package org.example.model;

/**
 * Alert and action counts for one chamber, shown in the daily report.
 * Alerts are recorded by the chamber thread and actions by the control thread,
 * so all access is synchronized.
 */
public class ChamberSummary {
    private int alerts = 0;
    private int mitigationActions = 0;
    private String lastAction = "No action";
    private String lastAlert = "None";

    public synchronized void recordAlert(String alert) {
        alerts++;
        lastAlert = alert;
    }

    public synchronized void recordAction(String action) {
        mitigationActions++;
        lastAction = action;
    }

    /** All four fields read together, as one consistent state. */
    public record Snapshot(int alerts, int mitigationActions, String lastAction, String lastAlert) {
    }

    /** All four fields under one lock, so a report line never mixes two states. */
    public synchronized Snapshot snapshot() {
        return new Snapshot(alerts, mitigationActions, lastAction, lastAlert);
    }
}
