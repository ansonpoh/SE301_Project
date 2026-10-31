package org.example.app;

import java.io.ByteArrayOutputStream;
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

    /** crop, sensor, HIGH|LOW, limit, device|-, water, energy, severity, message */
    public CropRegistry loadCrops(String resource, DeviceRegistry devices) {
        Map<String, List<ThresholdRule>> rulesByCrop = new LinkedHashMap<>();
        List<ThresholdRule> sharedRules = new ArrayList<>();

        for (Row row : readRows(resource, 9)) {
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

            if (f[0].equals(SHARED_RULE)) {
                sharedRules.add(rule);
            } else {
                rulesByCrop.computeIfAbsent(f[0], crop -> new ArrayList<>()).add(rule);
            }
        }

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
        for (Row row : readRows(resource, 2)) {
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
        for (Row row : readRows(resource, 3)) {
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
     * (with its BOM) to a UTF-8 file. Decode by BOM, dropping the BOM and NUL
     * characters those encodings leave behind. Any other byte that is not valid
     * text (e.g. an ANSI-encoded accent) is an error, never silently dropped.
     */
    private static String decode(String resource, byte[] bytes) {
        Charset charset = StandardCharsets.UTF_8;
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            charset = StandardCharsets.UTF_16LE;
        } else if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            charset = StandardCharsets.UTF_16BE;
        } else {
            bytes = withoutUtf16Boms(bytes);
        }
        try {
            return charset.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().replaceAll("[\\x{0}\\x{FEFF}]", "");
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(resource + ": not valid " + charset
                    + " text (save it as UTF-8)", e);
        }
    }

    /**
     * Drops each UTF-16 BOM (bytes FF FE or FE FF) appended to a UTF-8 file.
     * Those bytes never occur in UTF-8, so only a pair is a BOM; a lone 0xFF or
     * 0xFE (e.g. an ANSI "ÿ") is kept, so decoding rejects it.
     */
    private static byte[] withoutUtf16Boms(byte[] bytes) {
        ByteArrayOutputStream kept = new ByteArrayOutputStream(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            if (i + 1 < bytes.length && isUtf16Bom(bytes[i], bytes[i + 1])) {
                i++;
                continue;
            }
            kept.write(bytes[i]);
        }
        return kept.toByteArray();
    }

    private static boolean isUtf16Bom(byte first, byte second) {
        int a = first & 0xFF;
        int b = second & 0xFF;
        return (a == 0xFF && b == 0xFE) || (a == 0xFE && b == 0xFF);
    }

    private List<Row> readRows(String resource, int columns) {
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
            String[] fields = line.split(",", -1);   // -1 keeps trailing empty columns, so they are counted
            if (fields.length != columns) {
                throw new IllegalArgumentException(resource + " line " + lineNo
                        + ": expected " + columns + " columns but found " + fields.length);
            }
            for (int i = 0; i < fields.length; i++) {
                fields[i] = fields[i].strip();
            }
            rows.add(new Row(lineNo, fields));
        }
        return rows;
    }
}
