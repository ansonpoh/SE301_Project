package org.example.metrics;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.example.alert.Alert;
import org.example.control.Activation;

/**
 * Counts alerts and device actions per chamber.
 * <p>
 * The legacy {@code ChamberSummary} was mutated ({@code summary.alerts++}) by chamber threads and
 * the control thread without locking. Here each summary is an immutable {@link ChamberActivity}
 * replaced through {@link ConcurrentHashMap#compute}, which is atomic per chamber.
 */
public final class ChamberActivityTracker {

    private final Map<String, ChamberActivity> activityByChamber = new ConcurrentHashMap<>();

    public void onAlert(Alert alert) {
        activityByChamber.compute(alert.chamberId(),
                (id, current) -> orNone(current).withAlert(alert.message()));
    }

    public void onActivation(Activation activation) {
        activityByChamber.compute(activation.chamberId(),
                (id, current) -> orNone(current).withAction(activation.summary()));
    }

    public ChamberActivity activityOf(String chamberId) {
        return activityByChamber.getOrDefault(chamberId, ChamberActivity.NONE);
    }

    public SortedMap<String, ChamberActivity> snapshot() {
        return new TreeMap<>(activityByChamber);
    }

    private static ChamberActivity orNone(ChamberActivity activity) {
        return activity == null ? ChamberActivity.NONE : activity;
    }
}
