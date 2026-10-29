package org.example.alert;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps active and archived alerts.
 * <p>
 * The legacy code guarded alerts with {@code alertLock} and reports with a nested
 * {@code reportLock}; holding two locks at once creates a circular-wait (deadlock) risk as soon
 * as another path takes them in the opposite order. Here a single monitor protects all alert
 * state, and archiving moves alerts in one atomic step, so no thread ever holds two locks.
 */
public final class AlertStore {

    private final List<Alert> active = new ArrayList<>();
    private final List<Alert> archived = new ArrayList<>();
    private long totalGenerated;

    public synchronized void add(Alert alert) {
        active.add(alert);
        totalGenerated++;
    }

    /**
     * Moves every active alert to the archive.
     *
     * @return the number of alerts archived
     */
    public synchronized int archiveActive() {
        int count = active.size();
        archived.addAll(active);
        active.clear();
        return count;
    }

    public synchronized long totalGenerated() {
        return totalGenerated;
    }

    public synchronized List<Alert> activeAlerts() {
        return List.copyOf(active);
    }

    public synchronized List<Alert> archivedAlerts() {
        return List.copyOf(archived);
    }
}
