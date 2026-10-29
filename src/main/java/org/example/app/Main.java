package org.example.app;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.example.config.ChamberLayout;
import org.example.crop.CropRepository;
import org.example.crop.CsvCropRepository;
import org.example.model.Chamber;
import org.example.report.ArchiveReportWriter;
import org.example.report.FarmSnapshot;
import org.example.report.ReportGenerator;
import org.example.rule.StandardRules;
import org.example.simulation.RandomSensorSource;

/**
 * Composition root: the only place that chooses concrete implementations and wires them
 * together (Dependency Injection). Everything else depends on abstractions.
 */
public final class Main {

    private static final Path REPORT_FILE = Path.of("archive", "daily_report.txt");
    private static final double SENSOR_FAILURE_RATE = 0.01;

    private Main() {
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        CropRepository crops = CsvCropRepository.fromFile(CsvCropRepository.DEFAULT_FILE);
        List<Chamber> chambers = ChamberLayout.fromFile(ChamberLayout.DEFAULT_FILE, crops);
        System.out.println("Loaded " + crops.findAll().size() + " crop profiles. Monitoring "
                + describe(chambers));

        FarmSystem farm = new FarmSystem(chambers, new RandomSensorSource(SENSOR_FAILURE_RATE),
                StandardRules.create(), SimulationSettings.defaults());
        ConsoleEventPrinter console = new ConsoleEventPrinter(System.out);
        farm.onAlert(console::printAlert);
        farm.onActivation(console::printActivation);

        farm.runMonitoring();

        int archived = farm.archiveAlerts();
        System.out.println(archived > 0
                ? "Archiving alerts... " + archived + " moved to archive."
                : "Archiving alerts... no active alerts to archive.");

        FarmSnapshot snapshot = farm.snapshot();
        List<String> reports = ReportGenerator.standard().render(snapshot);
        reports.forEach(System.out::println);
        System.out.println("System finished sensor processing.");

        try {
            Path saved = new ArchiveReportWriter(REPORT_FILE).write(snapshot, reports);
            System.out.println("Archive report saved to: " + saved);
        } catch (IOException e) {
            System.err.println("Could not save archive report: " + e.getMessage());
        }
    }

    private static String describe(List<Chamber> chambers) {
        return chambers.stream()
                .map(chamber -> chamber.id() + " (" + chamber.crop().name() + ")")
                .collect(Collectors.joining(", "));
    }
}
