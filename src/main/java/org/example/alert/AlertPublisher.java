package org.example.alert;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends each alert to every listener, in the order they subscribed (Observer pattern).
 * Replaces the TRACK_ALERT / CREATE_ALERT branches of the legacy handleEvent().
 *
 * It is itself an AlertListener, so code that raises alerts depends only on
 * that interface and never knows how many listeners there are.
 *
 * A listener that throws is reported on its own and the others still run, so
 * one broken listener can neither hide the alert from the rest nor kill the
 * chamber thread that raised it (same rule as ControlService's ActionListeners).
 * Each failure is counted, and FarmSimulation fails the run if any happened,
 * since the report totals may then be short.
 */
public class AlertPublisher implements AlertListener {
    private final List<AlertListener> listeners = new CopyOnWriteArrayList<>();
    private final PrintStream err;
    private final AtomicInteger failedDeliveries = new AtomicInteger();

    /** @param err where a failing listener is reported */
    public AlertPublisher(PrintStream err) {
        this.err = err;
    }

    /** How many times a listener threw instead of taking an alert. */
    public int failedDeliveries() {
        return failedDeliveries.get();
    }

    public void subscribe(AlertListener listener) {
        listeners.add(listener);
    }

    @Override
    public void onAlert(Alert alert) {
        for (AlertListener listener : listeners) {
            try {
                listener.onAlert(alert);
            } catch (RuntimeException e) {
                failedDeliveries.incrementAndGet();
                err.println("Alert listener failed for " + alert.chamberId() + " (" + alert.message() + "): " + e);
            }
        }
    }
}
