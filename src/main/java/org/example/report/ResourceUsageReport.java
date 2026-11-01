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
import java.util.function.ToIntFunction;

/**
 * Spec 3.5 Water Usage and Energy Consumption reports: each chamber's use and
 * the facility total. Written to its own file, so the legacy console output
 * and daily report stay exactly as they were.
 *
 * Plugged in as one more DayEndOutput registered in SVFRMS, so FarmSimulation
 * needed no change to save it (OCP). Per-chamber figures did need a one-time
 * change underneath: UsageMeter's methods now name the chamber, so ChamberMonitor,
 * SensorProcessor and ResourceMeter pass it on. Reads the totals through
 * UsageTotals, not the concrete meter. Called on the main thread after the
 * chamber threads have stopped.
 */
public class ResourceUsageReport implements DayEndOutput {
    public static final String FILE_NAME = "resource_usage.txt";

    private final UsageTotals resources;
    private final Clock clock;

    public ResourceUsageReport(UsageTotals resources, Clock clock) {
        this.resources = resources;
        this.clock = clock;
    }

    @Override
    public String fileName() {
        return FILE_NAME;
    }

    /** e.g. "Resource usage: water 840 L, energy 510 kWh across 4 chambers" */
    @Override
    public String summaryLine() {
        SortedMap<String, UsageTotals.Usage> usage = resources.usageByChamber();
        return "Resource usage: water " + total(usage, UsageTotals.Usage::water) + " L, energy "
                + total(usage, UsageTotals.Usage::energy) + " kWh across " + usage.size() + " chambers";
    }

    /**
     * Both sections come from one snapshot, so they always list the same chambers.
     *
     * @throws IOException if the file cannot be written
     */
    @Override
    public void save(Path file, DayStatus day) throws IOException {
        SortedMap<String, UsageTotals.Usage> usage = resources.usageByChamber();
        List<String> lines = new ArrayList<>();
        lines.add("===== RESOURCE USAGE REPORT =====");
        lines.add("Timestamp: " + Timestamps.now(clock));
        addSection(lines, "--- water usage (L) ---", usage, UsageTotals.Usage::water);
        addSection(lines, "--- energy consumption (kWh) ---", usage, UsageTotals.Usage::energy);

        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /** One line per chamber, then the total. */
    private static void addSection(List<String> lines, String title, SortedMap<String, UsageTotals.Usage> usage,
                                   ToIntFunction<UsageTotals.Usage> amount) {
        lines.add(title);
        for (Map.Entry<String, UsageTotals.Usage> entry : usage.entrySet()) {
            lines.add(entry.getKey() + ": " + amount.applyAsInt(entry.getValue()));
        }
        lines.add("Total: " + total(usage, amount));
    }

    private static int total(SortedMap<String, UsageTotals.Usage> usage, ToIntFunction<UsageTotals.Usage> amount) {
        return usage.values().stream().mapToInt(amount).sum();
    }
}
