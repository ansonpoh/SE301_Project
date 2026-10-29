package org.example.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.example.alert.Alert;

/** Persists rendered reports and the archived alerts to a text file. */
public final class ArchiveReportWriter {

    private final Path file;

    public ArchiveReportWriter(Path file) {
        this.file = file;
    }

    /** @return the absolute path written */
    public Path write(FarmSnapshot snapshot, List<String> renderedReports) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("Timestamp: " + snapshot.generatedAt());
        lines.addAll(renderedReports);
        if (!snapshot.archivedAlerts().isEmpty()) {
            lines.add("--- archived alerts ---");
            snapshot.archivedAlerts().stream()
                    .map(alert -> "[" + alert.chamberId() + "][" + alert.cropName() + "] " + alert.describe())
                    .forEach(lines::add);
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file.toAbsolutePath();
    }
}
