package org.example.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.example.alert.Alert;
import org.example.alert.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeeklyCropReportTest {

    @TempDir
    Path dir;

    /** Chambers growing the same crop are summed, and a crop with no alerts or usage is still listed. */
    @Test
    void sumsChambersPerCrop() throws Exception {
        ChamberSummaryTracker summaries = new ChamberSummaryTracker();
        summaries.onAlert(new Alert("CH1", "Tomato", "CH1 Tomato High Temp", Severity.CRITICAL));
        summaries.onAlert(new Alert("CH2", "Tomato", "CH2 Tomato High Temp", Severity.CRITICAL));
        summaries.onAction("CH1", "Cooling Fan", "Temperature reduced to 26.0C");
        ResourceMeter resources = new ResourceMeter();
        resources.addWater("CH1", 40);
        resources.addWater("CH2", 2);
        resources.addEnergy("CH2", 10);

        WeeklyCropReport report = new WeeklyCropReport(
                Map.of("CH1", "Tomato", "CH2", "Tomato", "CH3", "Lettuce"), summaries, resources, Clock.systemUTC());
        Path file = dir.resolve(WeeklyCropReport.FILE_NAME);
        report.save(file);

        List<String> lines = Files.readAllLines(file);
        assertEquals(List.of(
                "Days recorded: 1 of 7 (one simulated day per run)",
                "--- per crop ---",
                "Lettuce -> Chambers=1, Alerts=0, Actions=0, Water=0 L, Energy=0 kWh",
                "Tomato -> Chambers=2, Alerts=2, Actions=1, Water=42 L, Energy=10 kWh",
                "Total -> Chambers=3, Alerts=2, Actions=1, Water=42 L, Energy=10 kWh"),
                lines.subList(2, lines.size()));
        assertEquals("Weekly crop report: 2 crops, day 1 of 7", report.summaryLine());
    }
}
