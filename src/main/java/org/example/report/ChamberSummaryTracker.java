package org.example.report;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.example.alert.Alert;
import org.example.alert.AlertListener;
import org.example.device.ActionListener;
import org.example.model.ChamberSummary;

/**
 * Keeps one ChamberSummary per chamber, created on the chamber's first alert
 * or action (as in the legacy code). Listens to both alerts and device actions.
 *
 * computeIfAbsent on a ConcurrentHashMap is atomic, which fixes the legacy
 * containsKey/put/get race that crashed chamber threads (issue C1).
 */
public class ChamberSummaryTracker implements AlertListener, ActionListener {
    private final Map<String, ChamberSummary> summaries = new ConcurrentHashMap<>();

    @Override
    public void onAlert(Alert alert) {
        summaryFor(alert.chamberId()).recordAlert(alert.message());
    }

    @Override
    public void onAction(String chamberId, String deviceName, String result) {
        recordAction(chamberId, deviceName + " Activated");
    }

    public void recordAction(String chamberId, String action) {
        summaryFor(chamberId).recordAction(action);
    }

    /** Summaries sorted by chamber id, for the report. */
    public SortedMap<String, ChamberSummary> sortedSummaries() {
        return new TreeMap<>(summaries);
    }

    private ChamberSummary summaryFor(String chamberId) {
        return summaries.computeIfAbsent(chamberId, id -> new ChamberSummary());
    }
}
