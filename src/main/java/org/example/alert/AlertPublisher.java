package org.example.alert;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sends each alert to every listener, in the order they subscribed (Observer pattern).
 * Replaces the TRACK_ALERT / CREATE_ALERT branches of the legacy handleEvent().
 */
public class AlertPublisher {
    private final List<AlertListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(AlertListener listener) {
        listeners.add(listener);
    }

    public void publish(Alert alert) {
        for (AlertListener listener : listeners) {
            listener.onAlert(alert);
        }
    }
}
