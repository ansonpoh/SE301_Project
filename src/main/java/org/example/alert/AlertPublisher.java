package org.example.alert;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sends each alert to every listener, in the order they subscribed (Observer pattern).
 * Replaces the TRACK_ALERT / CREATE_ALERT branches of the legacy handleEvent().
 *
 * It is itself an AlertListener, so code that raises alerts depends only on
 * that interface and never knows how many listeners there are.
 */
public class AlertPublisher implements AlertListener {
    private final List<AlertListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(AlertListener listener) {
        listeners.add(listener);
    }

    @Override
    public void onAlert(Alert alert) {
        for (AlertListener listener : listeners) {
            listener.onAlert(alert);
        }
    }
}
