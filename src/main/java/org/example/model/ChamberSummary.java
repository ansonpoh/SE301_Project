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

    public synchronized int alerts() {
        return alerts;
    }

    public synchronized int mitigationActions() {
        return mitigationActions;
    }

    public synchronized String lastAction() {
        return lastAction;
    }

    public synchronized String lastAlert() {
        return lastAlert;
    }
}
