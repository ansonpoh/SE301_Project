package org.example.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.example.alert.Alert;
import org.example.alert.Severity;
import org.example.crop.CropProfile;
import org.example.crop.CropRegistry;
import org.example.crop.CropRules;
import org.example.crop.ThresholdRule;
import org.example.model.SensorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeeklyCropReportTest {
    /** Tomato kept between 22 and 28 C, no other rules. */
    private static final CropRules CROPS = new CropRegistry(Map.of("Tomato", new CropProfile("Tomato",
            List.of(new ThresholdRule(SensorType.TEMPERATURE, ThresholdRule.Direction.HIGH, 28, null, 0, 0,
                            Severity.CRITICAL, "{crop} High Temp"),
                    new ThresholdRule(SensorType.TEMPERATURE, ThresholdRule.Direction.LOW, 22, null, 0, 0,
                            Severity.CRITICAL, "{crop} Low Temp")))), List.of());

    @TempDir
    Path dir;

    /** One simulated day: two Tomato chambers with alerts and usage, and a Lettuce chamber with none. */
    private static WeeklyCropReport oneDay() {
        ChamberSummaryTracker summaries = new ChamberSummaryTracker();
        summaries.onAlert(new Alert("CH1", "Tomato", "CH1 Tomato High Temp", Severity.CRITICAL));
        summaries.onAlert(new Alert("CH2", "Tomato", "CH2 Tomato High Temp", Severity.CRITICAL));
        summaries.onAction("CH1", "Cooling Fan", "Temperature reduced to 26.0C");
        ResourceMeter resources = new ResourceMeter();
        resources.addWater("CH1", 40);
        resources.addWater("CH2", 2);
        resources.addEnergy("CH2", 10);
        return new WeeklyCropReport(Map.of("CH1", "Tomato", "CH2", "Tomato", "CH3", "Lettuce"),
                summaries, resources, CROPS, Clock.systemUTC());
    }

    /** Chambers growing the same crop are summed, and a crop with no alerts or usage is still listed. */
    @Test
    void sumsChambersPerCrop() throws Exception {
        WeeklyCropReport report = oneDay();
        Path file = dir.resolve(WeeklyCropReport.FILE_NAME);
        report.save(file);

        List<String> lines = Files.readAllLines(file);
        assertEquals(List.of(
                "Days recorded: 1 of 7 (days 1-1; one simulated day per run, history in weekly_crop_history.csv)",
                "--- crop profiles (acceptable ranges from crops.csv) ---",
                "Lettuce: Temperature not monitored, Humidity not monitored, Soil not monitored,"
                        + " Light not monitored, Nutrient not monitored",
                "Tomato: Temperature 22-28 C, Humidity not monitored, Soil not monitored,"
                        + " Light not monitored, Nutrient not monitored",
                "--- per crop, summed over the days recorded ---",
                "Lettuce -> ChamberDays=1, Alerts=0, Actions=0, Water=0 L, Energy=0 kWh",
                "Tomato -> ChamberDays=2, Alerts=2, Actions=1, Water=42 L, Energy=10 kWh",
                "Total -> ChamberDays=3, Alerts=2, Actions=1, Water=42 L, Energy=10 kWh"),
                lines.subList(2, lines.size()));
        assertEquals("Weekly crop report: 2 crops, 1 of 7 days recorded", report.summaryLine());
    }

    /** Each run adds a day; the report sums the last seven and the history keeps only those. */
    @Test
    void sumsTheLastSevenDays() throws Exception {
        Path file = dir.resolve(WeeklyCropReport.FILE_NAME);
        for (int day = 1; day <= 9; day++) {
            oneDay().save(file);
        }

        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.contains("Days recorded: 7 of 7 (days 3-9; one simulated day per run,"
                + " history in weekly_crop_history.csv)"), String.join("\n", lines));
        assertTrue(lines.contains("Tomato -> ChamberDays=14, Alerts=14, Actions=7, Water=294 L, Energy=70 kWh"),
                String.join("\n", lines));
        List<String> history = Files.readAllLines(dir.resolve(WeeklyCropReport.HISTORY_FILE_NAME));
        assertEquals(1 + 7 * 2, history.size(), "header + 7 days x 2 crops");
        assertTrue(history.get(1).startsWith("3,"), history.get(1));
    }

    /** A damaged history fails the save instead of silently starting a new week. */
    @Test
    void damagedHistoryFailsTheSave() throws Exception {
        Files.writeString(dir.resolve(WeeklyCropReport.HISTORY_FILE_NAME), "1,2026-11-01,Tomato,1,x,0,0,0\n");

        IOException e = assertThrows(IOException.class, () -> oneDay().save(dir.resolve(WeeklyCropReport.FILE_NAME)));
        assertTrue(e.getMessage().contains("line 1") && e.getMessage().contains("delete the file"), e.getMessage());
    }
}
