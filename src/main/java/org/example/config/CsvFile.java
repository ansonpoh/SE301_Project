package org.example.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads simple comma-separated configuration files.
 * <p>
 * A file in the working directory takes precedence over the copy bundled in the jar, so crops
 * and chambers can be changed without rebuilding. Blank lines and lines starting with '#' are
 * ignored, and the first remaining line is treated as the header.
 */
public final class CsvFile {

    private CsvFile() {
    }

    /** Data rows (header excluded), each split into trimmed cells. */
    public static List<List<String>> readRows(String fileName) {
        List<String> lines = readLines(fileName).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
        return lines.stream()
                .skip(1)
                .map(line -> Arrays.stream(line.split(",")).map(String::trim).toList())
                .toList();
    }

    private static List<String> readLines(String fileName) {
        try {
            Path external = Path.of(fileName);
            if (Files.isRegularFile(external)) {
                return Files.readAllLines(external, StandardCharsets.UTF_8);
            }
            try (InputStream bundled = CsvFile.class.getClassLoader().getResourceAsStream(fileName)) {
                if (bundled == null) {
                    throw new IllegalArgumentException("Configuration file not found: " + fileName);
                }
                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(bundled, StandardCharsets.UTF_8))) {
                    reader.lines().forEach(lines::add);
                }
                return lines;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + fileName, e);
        }
    }
}
