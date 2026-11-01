package org.example.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.example.crop.CropRules;
import org.example.model.ChamberSummary;
import org.example.model.SensorType;

/**
 * Spec 3.5 Weekly Crop Report: alerts, device actions, water and energy per
 * crop over the last seven simulated days, plus each crop's profile (spec 3.1:
 * the acceptable range per sensor, worked out from its crops.csv rules).
 *
 * Each run simulates one day. Its per-crop figures are appended to
 * weekly_crop_history.csv next to the report, and the report sums the most
 * recent seven days in that file (fewer until a week has been recorded), so a
 * week of runs gives one weekly figure. Delete the history file to start a
 * new week. A history file that cannot be read fails the save (and so the
 * run) rather than silently starting over and losing the recorded days.
 *
 * One more DayEndOutput registered in SVFRMS: it only reads SummarySource,
 * UsageTotals and CropRules, so no existing class changed to add it (OCP).
 * Called on the main thread after every chamber and the control thread have stopped.
 */
public class WeeklyCropReport implements DayEndOutput {
    public static final String FILE_NAME = "weekly_crop_report.txt";
    public static final String HISTORY_FILE_NAME = "weekly_crop_history.csv";
    static final int DAYS_IN_WEEK = 7;
    private static final String HISTORY_HEADER = "# day,date,crop,chambers,alerts,actions,water,energy";

    /** One crop's figures, summed over its chambers (and, in the report, over the days). */
    record CropTotals(int chambers, int alerts, int actions, int water, int energy) {
        CropTotals plus(CropTotals other) {
            return new CropTotals(chambers + other.chambers, alerts + other.alerts, actions + other.actions,
                    water + other.water, energy + other.energy);
        }
    }

    /** One row of the history file: one crop on one simulated day. */
    private record DayRow(int day, String date, String crop, CropTotals totals) {
        String toCsv() {
            return day + "," + date + "," + crop + "," + totals.chambers() + "," + totals.alerts() + ","
                    + totals.actions() + "," + totals.water() + "," + totals.energy();
        }
    }

    private final SortedMap<String, String> cropByChamber;
    private final SummarySource summaries;
    private final UsageTotals resources;
    private final CropRules crops;
    private final Clock clock;
    /** Days in the last saved report; main thread only. */
    private int daysRecorded = 0;
    private int cropsReported = 0;

    /** @param cropByChamber every scheduled chamber and its crop, so a crop with no alerts is still listed */
    public WeeklyCropReport(Map<String, String> cropByChamber, SummarySource summaries,
                            UsageTotals resources, CropRules crops, Clock clock) {
        this.cropByChamber = new TreeMap<>(cropByChamber);
        this.summaries = summaries;
        this.resources = resources;
        this.crops = crops;
        this.clock = clock;
    }

    @Override
    public String fileName() {
        return FILE_NAME;
    }

    /** e.g. "Weekly crop report: 4 crops, 3 of 7 days recorded" (after save) */
    @Override
    public String summaryLine() {
        return "Weekly crop report: " + cropsReported + " crops, " + daysRecorded + " of " + DAYS_IN_WEEK
                + " days recorded";
    }

    /**
     * Records today in the history file, then writes the report for the last
     * seven days in it.
     *
     * @throws IOException if either file cannot be read or written
     */
    @Override
    public void save(Path file) throws IOException {
        Path historyFile = file.resolveSibling(HISTORY_FILE_NAME);
        List<DayRow> history = readHistory(historyFile);
        int today = history.isEmpty() ? 1 : history.getLast().day() + 1;
        String date = LocalDate.now(clock).toString();
        totalsByCrop().forEach((crop, totals) -> history.add(new DayRow(today, date, crop, totals)));

        // Keep only the days this and the next six reports can still include.
        List<DayRow> week = history.stream().filter(row -> row.day() > today - DAYS_IN_WEEK).toList();
        writeHistory(historyFile, week);

        int firstDay = week.isEmpty() ? today : week.getFirst().day();   // empty only with no chambers at all
        SortedMap<String, CropTotals> byCrop = new TreeMap<>();
        for (DayRow row : week) {
            byCrop.merge(row.crop(), row.totals(), CropTotals::plus);
        }

        List<String> lines = new ArrayList<>();
        lines.add("===== WEEKLY CROP REPORT =====");
        lines.add("Timestamp: " + Timestamps.now(clock));
        lines.add("Days recorded: " + (today - firstDay + 1) + " of " + DAYS_IN_WEEK + " (days " + firstDay + "-"
                + today + "; one simulated day per run, history in " + HISTORY_FILE_NAME + ")");
        lines.add("--- crop profiles (acceptable ranges from crops.csv) ---");
        for (String crop : byCrop.keySet()) {
            lines.add(crop + ": " + profile(crop));
        }
        lines.add("--- per crop, summed over the days recorded ---");
        CropTotals all = new CropTotals(0, 0, 0, 0, 0);
        for (Map.Entry<String, CropTotals> entry : byCrop.entrySet()) {
            lines.add(entry.getKey() + " -> " + describe(entry.getValue()));
            all = all.plus(entry.getValue());
        }
        lines.add("Total -> " + describe(all));

        Files.write(file, lines, StandardCharsets.UTF_8);   // writeHistory created the folder
        daysRecorded = today - firstDay + 1;
        cropsReported = byCrop.size();
    }

    /** Sums each chamber's figures for today into its crop, from one snapshot of each source. */
    SortedMap<String, CropTotals> totalsByCrop() {
        SortedMap<String, ChamberSummary.Snapshot> chamberSummaries = summaries.sortedSummaries();
        SortedMap<String, UsageTotals.Usage> usage = resources.usageByChamber();
        SortedMap<String, CropTotals> totals = new TreeMap<>();
        cropByChamber.forEach((chamber, crop) -> {
            ChamberSummary.Snapshot s = chamberSummaries.get(chamber);
            UsageTotals.Usage u = usage.getOrDefault(chamber, new UsageTotals.Usage(0, 0));
            CropTotals chamberTotals = new CropTotals(1, s == null ? 0 : s.alerts(),
                    s == null ? 0 : s.mitigationActions(), u.water(), u.energy());
            totals.merge(crop, chamberTotals, CropTotals::plus);
        });
        return totals;
    }

    /** e.g. "Temperature 22-28 C, Humidity 60-80 %, Soil >= 45 %, Light >= 500 lux, Nutrient >= 40 %" */
    private String profile(String crop) {
        List<String> ranges = new ArrayList<>();
        for (SensorType sensor : SensorType.values()) {
            ranges.add(crops.rangeFor(crop, sensor).describe());
        }
        return String.join(", ", ranges);
    }

    private static String describe(CropTotals t) {
        return "ChamberDays=" + t.chambers() + ", Alerts=" + t.alerts() + ", Actions=" + t.actions()
                + ", Water=" + t.water() + " L, Energy=" + t.energy() + " kWh";
    }

    /** The rows recorded so far, in day order, or none if there is no history file yet. */
    private static List<DayRow> readHistory(Path historyFile) throws IOException {
        List<DayRow> rows = new ArrayList<>();
        if (!Files.exists(historyFile)) {
            return rows;
        }
        int lineNo = 0;
        for (String line : Files.readAllLines(historyFile, StandardCharsets.UTF_8)) {
            lineNo++;
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split(",", -1);
            try {
                if (f.length != 8) {
                    throw new IllegalArgumentException("expected 8 columns but found " + f.length);
                }
                DayRow row = new DayRow(Integer.parseInt(f[0]), f[1], f[2], new CropTotals(Integer.parseInt(f[3]),
                        Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]),
                        Integer.parseInt(f[7])));
                if (!rows.isEmpty() && row.day() < rows.getLast().day()) {
                    throw new IllegalArgumentException("day " + row.day() + " is out of order");
                }
                rows.add(row);
            } catch (IllegalArgumentException e) {
                throw new IOException(historyFile + " line " + lineNo + ": " + e.getMessage()
                        + " (fix it, or delete the file to start a new week)", e);
            }
        }
        return rows;
    }

    /** Written to a temporary file first, so a failed write never leaves half a history. */
    private static void writeHistory(Path historyFile, List<DayRow> rows) throws IOException {
        Files.createDirectories(historyFile.toAbsolutePath().getParent());
        List<String> lines = new ArrayList<>();
        lines.add(HISTORY_HEADER);
        rows.forEach(row -> lines.add(row.toCsv()));
        Path temp = historyFile.resolveSibling(HISTORY_FILE_NAME + ".tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temp, historyFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, historyFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
