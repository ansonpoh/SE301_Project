package org.example.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.alert.Severity;
import org.example.crop.CropProfile;
import org.example.crop.CropRegistry;
import org.example.crop.ThresholdRule;
import org.example.device.DeviceRegistry;
import org.example.model.SensorType;
import org.example.sensor.SensorRanges;

/**
 * Reads crops.csv, chambers.csv and sensors.csv. Adding or removing a crop is an edit to
 * those files only; no Java changes.
 *
 * The built-in files come from the classpath (src/main/resources). If a config
 * folder is given (java -jar run.jar &lt;folder&gt;), a file with the same name in
 * that folder is used instead, and a notice says so. Only an explicit folder is
 * searched, so a stray CSV in the working directory can never change a run.
 *
 * Lines that are blank or start with '#' are ignored. Every error names the
 * file and line, and is raised before any thread starts.
 */
public class FarmConfigLoader {
    private static final String SHARED_RULE = "*";
    private static final String NO_DEVICE = "-";

    public static final String CROPS_FILE = "crops.csv";
    public static final String CHAMBERS_FILE = "chambers.csv";
    public static final String SENSORS_FILE = "sensors.csv";
    /** Every file a config folder may override. */
    public static final List<String> CONFIG_FILES = List.of(CROPS_FILE, CHAMBERS_FILE, SENSORS_FILE);

    private final Path configDir;
    private final PrintStream notices;

    /** Built-in files only; warnings go to System.err. */
    public FarmConfigLoader() {
        this(null, System.err);
    }

    /**
     * @param configDir folder whose files override the built-in ones, or null for none
     * @param notices   where to say which override file was used, and print warnings
     *                  (null discards them, e.g. in tests that check only the result)
     */
    public FarmConfigLoader(Path configDir, PrintStream notices) {
        this.configDir = configDir;
        this.notices = notices;
    }

    private record Row(int lineNo, String[] fields) {
    }

    /**
     * crop, sensor, HIGH|LOW, limit, device|-, water, energy, severity, message.
     * The message is the last column, so it may contain commas.
     */
    public CropRegistry loadCrops(String resource, DeviceRegistry devices) {
        Map<String, List<ThresholdRule>> rulesByCrop = new LinkedHashMap<>();
        List<ThresholdRule> sharedRules = new ArrayList<>();
        Map<List<Object>, Integer> firstLineOfRule = new HashMap<>();
        Map<List<Object>, Row> cropRuleRows = new LinkedHashMap<>();

        for (Row row : readRows(resource, 9, true)) {
            String[] f = row.fields();
            String device = f[4].equals(NO_DEVICE) ? null : f[4];
            if (device != null && !devices.contains(device)) {
                throw error(resource, row, "unknown device '" + device + "'");
            }
            ThresholdRule rule;
            try {
                rule = new ThresholdRule(
                        SensorType.valueOf(f[1]), ThresholdRule.Direction.valueOf(f[2]), Double.parseDouble(f[3]),
                        device, Integer.parseInt(f[5]), Integer.parseInt(f[6]), Severity.valueOf(f[7]), f[8]);
            } catch (IllegalArgumentException e) {
                throw error(resource, row, e.getMessage());
            }
            // Two rows that fire on the same readings (e.g. a copy-pasted row with
            // new text) would double that alert, its costs and its device request.
            // Different limits are allowed, e.g. a WARNING and a CRITICAL level.
            List<Object> trigger = List.of(f[0], rule.sensor(), rule.direction(), rule.limit());
            Integer firstLine = firstLineOfRule.putIfAbsent(trigger, row.lineNo());
            if (firstLine != null) {
                throw error(resource, row, "same crop, sensor, direction and limit as line " + firstLine);
            }

            if (f[0].equals(SHARED_RULE)) {
                sharedRules.add(rule);
            } else {
                rulesByCrop.computeIfAbsent(f[0], crop -> new ArrayList<>()).add(rule);
                cropRuleRows.put(trigger, row);
            }
        }
        // A crop row that repeats a shared row fires twice for that crop as well.
        // Shared rows usually come last in the file, so this is checked at the end.
        cropRuleRows.forEach((trigger, row) -> {
            List<Object> shared = new ArrayList<>(trigger);
            shared.set(0, SHARED_RULE);
            Integer sharedLine = firstLineOfRule.get(shared);
            if (sharedLine != null) {
                throw error(resource, row, "same sensor, direction and limit as the shared rule on line "
                        + sharedLine);
            }
        });

        Map<String, CropProfile> profiles = new LinkedHashMap<>();
        rulesByCrop.forEach((crop, rules) -> profiles.put(crop, new CropProfile(crop, rules)));
        return new CropRegistry(profiles, sharedRules);
    }

    /**
     * chamber, crop. A crop with no rows in crops.csv runs with only the shared
     * rules, exactly as in the legacy code, but it is usually a typo or a crop
     * removed from crops.csv, so each one gets a warning at start-up.
     */
    public List<ChamberAssignment> loadChambers(String resource, CropRegistry crops) {
        List<ChamberAssignment> chambers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Row row : readRows(resource, 2, false)) {
            String[] f = row.fields();
            if (!seen.add(f[0])) {
                throw error(resource, row, "chamber '" + f[0] + "' is listed twice");
            }
            if (!crops.contains(f[1])) {
                notice("WARNING: " + resource + " line " + row.lineNo() + ": crop '" + f[1]
                        + "' has no rows in crops.csv, so " + f[0] + " gets only the shared rules");
            }
            chambers.add(new ChamberAssignment(f[0], f[1]));
        }
        if (chambers.isEmpty()) {
            throw new IllegalArgumentException(resource + ": no chambers listed");
        }
        return chambers;
    }

    /** sensor, min, max. Every sensor type must be listed exactly once. */
    public SensorRanges loadSensorRanges(String resource) {
        Map<SensorType, SensorRanges.Range> ranges = new EnumMap<>(SensorType.class);
        for (Row row : readRows(resource, 3, false)) {
            String[] f = row.fields();
            SensorType sensor;
            SensorRanges.Range range;
            try {
                sensor = SensorType.valueOf(f[0]);
                range = new SensorRanges.Range(Double.parseDouble(f[1]), Double.parseDouble(f[2]));
            } catch (IllegalArgumentException e) {
                throw error(resource, row, e.getMessage());
            }
            if (ranges.put(sensor, range) != null) {
                throw error(resource, row, "sensor " + sensor + " is listed twice");
            }
        }
        try {
            return new SensorRanges(ranges);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(resource + ": " + e.getMessage(), e);
        }
    }

    private void notice(String message) {
        if (notices != null) {
            notices.println(message);
        }
    }

    private static IllegalArgumentException error(String resource, Row row, String message) {
        return new IllegalArgumentException(resource + " line " + row.lineNo() + ": " + message);
    }

    private String read(String resource) throws IOException {
        if (configDir != null) {
            Path file = configDir.resolve(resource);
            if (Files.isRegularFile(file)) {
                notice("Using " + file + " instead of the built-in " + resource);
                return decode(resource, Files.readAllBytes(file));
            }
        }
        try (InputStream in = FarmConfigLoader.class.getResourceAsStream("/" + resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + resource);
            }
            return decode(resource, in.readAllBytes());
        }
    }

    /**
     * Config files are UTF-8, but Windows tools often save them as UTF-16 or
     * with a byte-order mark, and PowerShell 5.1's "&gt;&gt;" appends UTF-16 text
     * (with its BOM) to a UTF-8 file. So the bytes are split at each UTF-16 BOM
     * (FF FE or FE FF, which never occur in UTF-8) and every part is decoded in
     * its own encoding: UTF-8 up to the first BOM, then whatever each BOM names.
     * A byte that is not valid text in its part (e.g. an ANSI accent, a lone
     * 0xFF) is an error, never silently dropped.
     */
    private static String decode(String resource, byte[] bytes) {
        StringBuilder text = new StringBuilder();
        Charset charset = StandardCharsets.UTF_8;
        int start = 0;
        int i = 0;
        while (i + 1 < bytes.length) {
            Charset next = utf16Bom(bytes[i], bytes[i + 1]);
            if (next != null) {
                text.append(decodePart(resource, bytes, start, i, charset));
                charset = next;
                start = i + 2;
                i = start;
            } else {
                // UTF-16 text comes in 2-byte units, so a BOM can only start on one.
                i += charset.equals(StandardCharsets.UTF_8) ? 1 : 2;
            }
        }
        text.append(decodePart(resource, bytes, start, bytes.length, charset));
        return text.toString().replace("\uFEFF", "");   // a UTF-8 BOM decodes to U+FEFF
    }

    private static String decodePart(String resource, byte[] bytes, int from, int to, Charset charset) {
        try {
            return charset.newDecoder().decode(ByteBuffer.wrap(bytes, from, to - from)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(resource + ": not valid " + charset
                    + " text (save it as UTF-8)", e);
        }
    }

    /** The charset a UTF-16 BOM names, or null if these two bytes are not one. */
    private static Charset utf16Bom(byte first, byte second) {
        int a = first & 0xFF;
        int b = second & 0xFF;
        if (a == 0xFF && b == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        if (a == 0xFE && b == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        return null;
    }

    /** @param lastColumnIsText the last column keeps any commas in it (free text, e.g. an alert message) */
    private List<Row> readRows(String resource, int columns, boolean lastColumnIsText) {
        String text;
        try {
            text = read(resource);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<Row> rows = new ArrayList<>();
        int lineNo = 0;
        for (String line : text.split("\\R", -1)) {
            lineNo++;
            if (line.isBlank() || line.strip().startsWith("#")) {
                continue;
            }
            // -1 keeps trailing empty columns, so they are counted
            String[] fields = line.split(",", lastColumnIsText ? columns : -1);
            if (fields.length != columns) {
                throw new IllegalArgumentException(resource + " line " + lineNo
                        + ": expected " + columns + " columns but found " + fields.length);
            }
            for (int i = 0; i < fields.length; i++) {
                fields[i] = fields[i].strip();
                if (fields[i].isEmpty()) {
                    throw new IllegalArgumentException(resource + " line " + lineNo
                            + ": column " + (i + 1) + " is empty");
                }
            }
            rows.add(new Row(lineNo, fields));
        }
        return rows;
    }
}
