package org.example.report;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A file written next to the daily report at the end of the day, with one
 * console line saying what it holds. FarmSimulation saves every one it is
 * given, so a new end-of-day output (e.g. a weekly or nutrient report) is a
 * new implementation registered in SVFRMS, with no change to FarmSimulation (OCP).
 */
public interface DayEndOutput {

    /** e.g. "alerts_by_severity.txt" */
    String fileName();

    /** @throws IOException if the file cannot be written */
    void save(Path file) throws IOException;

    /** e.g. "Alerts by severity: INFO=3, WARNING=10, CRITICAL=40" */
    String summaryLine();
}
