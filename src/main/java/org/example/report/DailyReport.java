package org.example.report;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import org.example.alert.AlertStore;
import org.example.model.ChamberSummary;

/**
 * End-of-day steps: archive alerts, print the daily report, save the report file.
 * The only place the report format is defined (the legacy code repeated it twice).
 *
 * Called from the main thread after all chamber and control threads have
 * finished, and always in the order archive -> print -> save (legacy issue C6).
 */
public class DailyReport {
    private static final String HEADER = "===== DAILY REPORT =====";

    private final AlertStore alerts;
    private final ResourceMeter resources;
    private final ChamberSummaryTracker summaries;
    private final PrintStream out;
    private final Clock clock;
    /** "Archived N alerts", "Daily Report", ... in the order they happened. Main thread only. */
    private final List<String> reportLog = new ArrayList<>();

    public DailyReport(AlertStore alerts, ResourceMeter resources,
                       ChamberSummaryTracker summaries, PrintStream out, Clock clock) {
        this.alerts = alerts;
        this.resources = resources;
        this.summaries = summaries;
        this.out = out;
        this.clock = clock;
    }

    public void archiveAlerts() {
        int moved = alerts.archiveActive().size();
        if (moved > 0) {
            reportLog.add("Archived " + moved + " alerts");
            out.println("Archiving alerts... " + moved + " moved to archive.");
        } else {
            reportLog.add("No alerts to archive");
            out.println("Archiving alerts... no active alerts to archive.");
        }
    }

    public void print() {
        out.println(HEADER);
        totals().forEach(out::println);
        reportLog.add("Daily Report");
    }

    public void save(Path file) {
        List<String> archived = alerts.archived();
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.add("Timestamp: " + Timestamps.now(clock));
        lines.addAll(totals());
        lines.add("Archived alerts: " + archived.size());
        lines.add("---");
        lines.addAll(reportLog);

        SortedMap<String, ChamberSummary> chambers = summaries.sortedSummaries();
        if (!chambers.isEmpty()) {
            lines.add("--- chamber wise summary ---");
            for (Map.Entry<String, ChamberSummary> entry : chambers.entrySet()) {
                ChamberSummary s = entry.getValue();
                lines.add(entry.getKey() + " -> Alerts=" + s.alerts() + ", Actions=" + s.mitigationActions()
                        + ", Last Action=" + s.lastAction() + ", Last Alert=" + s.lastAlert());
            }
        }
        if (!archived.isEmpty()) {
            lines.add("--- archived alerts ---");
            lines.addAll(archived);
        }

        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
            out.println("Archive report saved to: " + file.toAbsolutePath());
        } catch (IOException e) {
            out.println("Could not save archive report: " + e.getMessage());
        }
    }

    private List<String> totals() {
        return List.of(
                "Generated Alerts: " + alerts.generatedCount(),
                "Active Alerts: " + alerts.activeCount(),
                "Water Usage: " + resources.water() + " L",
                "Energy Usage: " + resources.energy() + " kWh");
    }
}
