package org.example.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import org.example.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NutrientUsageReportTest {

    @TempDir
    Path dir;

    /** Only the nutrient device counts, and a chamber with no doses is still listed. */
    @Test
    void countsNutrientDosesPerChamber() throws Exception {
        NutrientUsageReport report = new NutrientUsageReport("Nutrient Injector", List.of("CH2", "CH1", "CH3"),
                Clock.systemUTC());
        report.onAction("CH1", "Nutrient Injector", "Nutrient level increased to 75.0%");
        report.onAction("CH1", "Nutrient Injector", "Nutrient level increased to 90.0%");
        report.onAction("CH2", "Nutrient Injector", "Nutrient level increased to 55.0%");
        report.onAction("CH1", "Cooling Fan", "Temperature reduced to 26.0C");

        Path file = dir.resolve(NutrientUsageReport.FILE_NAME);
        report.save(file);

        List<String> lines = Files.readAllLines(file);
        assertEquals(List.of("--- Nutrient Injector doses ---", "CH1: 2", "CH2: 1", "CH3: 0", "Total: 3"),
                lines.subList(2, lines.size()));
        assertEquals("Nutrient usage: 3 Nutrient Injector doses across 3 chambers", report.summaryLine());
    }

    /** The control thread is the only caller today, but the counts stay exact under concurrent calls too. */
    @Test
    void losesNoDosesUnderConcurrentCalls() throws Exception {
        NutrientUsageReport report = new NutrientUsageReport("Nutrient Injector", List.of("CH1"), Clock.systemUTC());

        TestFixtures.runConcurrently(8, () -> {
            for (int i = 0; i < 1000; i++) {
                report.onAction("CH1", "Nutrient Injector", "");
            }
        });

        assertEquals(8000, report.doses("CH1"));
    }
}
