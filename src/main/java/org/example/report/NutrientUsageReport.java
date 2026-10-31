package org.example.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.example.device.ActionListener;

/**
 * Spec 3.5 Nutrient Consumption report: how many nutrient doses each chamber
 * received and the facility total. A dose is one run of the nutrient device,
 * which the legacy code already performed but never counted. Written to its
 * own file, so the legacy console output and daily report stay exactly as they were.
 *
 * Added as one more ActionListener and DayEndOutput, both registered in SVFRMS:
 * the devices, ControlService and FarmSimulation did not change (Observer, OCP).
 * onAction runs on the control thread, so the counts are atomic; the report is
 * read on the main thread after the control thread has stopped.
 */
public class NutrientUsageReport implements ActionListener, DayEndOutput {
    public static final String FILE_NAME = "nutrient_usage.txt";

    private final String nutrientDevice;
    private final SortedSet<String> chambers;
    private final Clock clock;
    private final ConcurrentMap<String, AtomicInteger> doses = new ConcurrentHashMap<>();

    /**
     * @param nutrientDevice name of the device that doses nutrients (e.g. "Nutrient Injector")
     * @param chambers       every scheduled chamber, so one with no doses is listed with 0
     */
    public NutrientUsageReport(String nutrientDevice, List<String> chambers, Clock clock) {
        this.nutrientDevice = nutrientDevice;
        this.chambers = new TreeSet<>(chambers);
        this.clock = clock;
    }

    @Override
    public void onAction(String chamberId, String deviceName, String result) {
        if (deviceName.equals(nutrientDevice)) {
            doses.computeIfAbsent(chamberId, id -> new AtomicInteger()).incrementAndGet();
        }
    }

    public int doses(String chamberId) {
        AtomicInteger count = doses.get(chamberId);
        return count == null ? 0 : count.get();
    }

    @Override
    public String fileName() {
        return FILE_NAME;
    }

    /** e.g. "Nutrient usage: 12 Nutrient Injector doses across 4 chambers" */
    @Override
    public String summaryLine() {
        return "Nutrient usage: " + total() + " " + nutrientDevice + " doses across " + chambers.size() + " chambers";
    }

    /** @throws IOException if the file cannot be written */
    @Override
    public void save(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("===== NUTRIENT CONSUMPTION REPORT =====");
        lines.add("Timestamp: " + Timestamps.now(clock));
        lines.add("--- " + nutrientDevice + " doses ---");
        for (String chamber : chambers) {
            lines.add(chamber + ": " + doses(chamber));
        }
        lines.add("Total: " + total());

        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private int total() {
        return chambers.stream().mapToInt(this::doses).sum();
    }
}
