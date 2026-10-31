package org.example.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.example.alert.Alert;
import org.example.alert.AlertListener;
import org.example.alert.Severity;

/**
 * Every alert with its severity, e.g. "CRITICAL: CH1 Tomato High Temp", plus a
 * count per level (spec 3.4). Written to its own file so the legacy console
 * output and daily report stay exactly as they were.
 *
 * Added as one more AlertListener: neither the rule engine nor the other
 * listeners changed (Observer, OCP). Chamber threads call onAlert at the same
 * time, so all access is synchronized on this log.
 */
public class SeverityLog implements AlertListener, DayEndOutput {
    public static final String FILE_NAME = "alerts_by_severity.txt";

    private final List<String> entries = new ArrayList<>();
    private final Map<Severity, Integer> counts = new EnumMap<>(Severity.class);

    @Override
    public synchronized void onAlert(Alert alert) {
        entries.add(alert.severity() + ": " + alert.message());
        counts.merge(alert.severity(), 1, Integer::sum);
    }

    public synchronized int count(Severity severity) {
        return counts.getOrDefault(severity, 0);
    }

    public synchronized List<String> entries() {
        return List.copyOf(entries);
    }

    /** One count per level, e.g. "INFO=3, WARNING=10, CRITICAL=40". */
    public synchronized String summary() {
        List<String> parts = new ArrayList<>();
        for (Severity severity : Severity.values()) {
            parts.add(severity + "=" + count(severity));
        }
        return String.join(", ", parts);
    }

    @Override
    public String fileName() {
        return FILE_NAME;
    }

    @Override
    public String summaryLine() {
        return "Alerts by severity: " + summary();
    }

    /**
     * The counts and the entries are taken under one lock, so they always agree,
     * even if a chamber that did not stop in time is still raising alerts.
     *
     * @throws IOException if the file cannot be written
     */
    @Override
    public void save(Path file) throws IOException {
        List<String> lines = snapshot();
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private synchronized List<String> snapshot() {
        List<String> lines = new ArrayList<>();
        lines.add("===== ALERTS BY SEVERITY =====");
        for (Severity severity : Severity.values()) {
            lines.add(severity + ": " + count(severity));
        }
        lines.add("---");
        lines.addAll(entries);
        return lines;
    }
}
