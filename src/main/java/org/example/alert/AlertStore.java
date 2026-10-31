package org.example.alert;

import java.util.ArrayList;
import java.util.List;

/**
 * Active and archived alerts plus the "generated" count.
 *
 * Every chamber thread adds alerts at the same time, so every method is
 * synchronized on this store. One lock guards all three fields, so adding
 * and archiving can never see each other half-done (legacy issues C2, C7).
 */
public class AlertStore implements AlertListener {
    private final List<String> active = new ArrayList<>();
    private final List<String> archived = new ArrayList<>();
    private int generated = 0;

    @Override
    public synchronized void onAlert(Alert alert) {
        active.add(alert.message());
        generated++;
    }

    /** Moves every active alert to the archive and returns the ones moved. */
    public synchronized List<String> archiveActive() {
        List<String> moved = new ArrayList<>(active);
        archived.addAll(moved);
        active.clear();
        return moved;
    }

    public synchronized int generatedCount() {
        return generated;
    }

    public synchronized int activeCount() {
        return active.size();
    }

    public synchronized List<String> archived() {
        return List.copyOf(archived);
    }
}
