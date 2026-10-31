package org.example.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Timestamped log of every reading processed. The legacy code collected it
 * but never used it; it is now saved as archive/sensor_log.txt, a record of
 * the day's environmental readings. Chamber threads record at the same time,
 * so all access is synchronized on this log.
 */
public class SensorLog implements DayEndOutput {
    public static final String FILE_NAME = "sensor_log.txt";

    private final List<String> entries = new ArrayList<>();
    private final Clock clock;

    public SensorLog(Clock clock) {
        this.clock = clock;
    }

    public void record(String message) {
        String entry = Timestamps.now(clock) + " : " + message;   // formatted outside the lock
        synchronized (this) {
            entries.add(entry);
        }
    }

    public synchronized int size() {
        return entries.size();
    }

    @Override
    public String fileName() {
        return FILE_NAME;
    }

    @Override
    public String summaryLine() {
        return "Sensor log: " + size() + " readings";
    }

    /** @throws IOException if the file cannot be written */
    @Override
    public void save(Path file) throws IOException {
        List<String> lines;
        synchronized (this) {
            lines = new ArrayList<>(entries);
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
    }
}
