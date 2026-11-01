package org.example.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.example.model.ChamberSummary;

/**
 * Spec 3.5 Weekly Crop Report: alerts, device actions, water and energy per
 * crop, summed over the chambers that grow it. The program simulates one day
 * per run, so the report covers the days recorded so far (1 of 7) and states
 * that, rather than inventing figures for days that were never simulated.
 * Written to its own file, so the legacy console output and daily report stay
 * exactly as they were.
 *
 * One more DayEndOutput registered in SVFRMS: it only reads ChamberSummaryTracker
 * and UsageTotals, so no existing class changed to add it (OCP). Called on the
 * main thread after every chamber and the control thread have stopped.
 */
public class WeeklyCropReport implements DayEndOutput {
    public static final String FILE_NAME = "weekly_crop_report.txt";
    private static final int DAYS_IN_WEEK = 7;
    private static final int DAYS_RECORDED = 1;

    /** One crop's figures, summed over its chambers. */
    record CropTotals(int chambers, int alerts, int actions, int water, int energy) {
        CropTotals plus(CropTotals other) {
            return new CropTotals(chambers + other.chambers, alerts + other.alerts, actions + other.actions,
                    water + other.water, energy + other.energy);
        }
    }

    private final SortedMap<String, String> cropByChamber;
    private final ChamberSummaryTracker summaries;
    private final UsageTotals resources;
    private final Clock clock;

    /** @param cropByChamber every scheduled chamber and its crop, so a crop with no alerts is still listed */
    public WeeklyCropReport(Map<String, String> cropByChamber, ChamberSummaryTracker summaries,
                            UsageTotals resources, Clock clock) {
        this.cropByChamber = new TreeMap<>(cropByChamber);
        this.summaries = summaries;
        this.resources = resources;
        this.clock = clock;
    }

    @Override
    public String fileName() {
        return FILE_NAME;
    }

    /** e.g. "Weekly crop report: 4 crops, day 1 of 7" */
    @Override
    public String summaryLine() {
        return "Weekly crop report: " + totalsByCrop().size() + " crops, day " + DAYS_RECORDED + " of " + DAYS_IN_WEEK;
    }

    /** @throws IOException if the file cannot be written */
    @Override
    public void save(Path file) throws IOException {
        SortedMap<String, CropTotals> crops = totalsByCrop();
        List<String> lines = new ArrayList<>();
        lines.add("===== WEEKLY CROP REPORT =====");
        lines.add("Timestamp: " + Timestamps.now(clock));
        lines.add("Days recorded: " + DAYS_RECORDED + " of " + DAYS_IN_WEEK + " (one simulated day per run)");
        lines.add("--- per crop ---");
        CropTotals all = new CropTotals(0, 0, 0, 0, 0);
        for (Map.Entry<String, CropTotals> entry : crops.entrySet()) {
            lines.add(entry.getKey() + " -> " + describe(entry.getValue()));
            all = all.plus(entry.getValue());
        }
        lines.add("Total -> " + describe(all));

        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /** Sums each chamber's figures into its crop, from one snapshot of each source. */
    SortedMap<String, CropTotals> totalsByCrop() {
        SortedMap<String, ChamberSummary> chamberSummaries = summaries.sortedSummaries();
        SortedMap<String, UsageTotals.Usage> usage = resources.usageByChamber();
        SortedMap<String, CropTotals> crops = new TreeMap<>();
        cropByChamber.forEach((chamber, crop) -> {
            ChamberSummary s = chamberSummaries.get(chamber);
            UsageTotals.Usage u = usage.getOrDefault(chamber, new UsageTotals.Usage(0, 0));
            CropTotals chamberTotals = new CropTotals(1, s == null ? 0 : s.alerts(),
                    s == null ? 0 : s.mitigationActions(), u.water(), u.energy());
            crops.merge(crop, chamberTotals, CropTotals::plus);
        });
        return crops;
    }

    private static String describe(CropTotals t) {
        return "Chambers=" + t.chambers() + ", Alerts=" + t.alerts() + ", Actions=" + t.actions()
                + ", Water=" + t.water() + " L, Energy=" + t.energy() + " kWh";
    }
}
