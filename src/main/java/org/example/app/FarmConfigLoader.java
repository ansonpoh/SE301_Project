package org.example.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.crop.CropProfile;
import org.example.crop.CropRegistry;
import org.example.crop.ThresholdRule;
import org.example.device.DeviceRegistry;
import org.example.model.SensorType;

/**
 * Reads crops.csv and chambers.csv from the classpath (src/main/resources).
 * Adding or removing a crop is an edit to those files only; no Java changes.
 * Lines that are blank or start with '#' are ignored.
 */
public class FarmConfigLoader {
    private static final String SHARED_RULE = "*";
    private static final String NO_DEVICE = "-";

    /** crop, sensor, HIGH|LOW, limit, device|-, water, energy, message */
    public CropRegistry loadCrops(String resource, DeviceRegistry devices) {
        Map<String, List<ThresholdRule>> rulesByCrop = new LinkedHashMap<>();
        List<ThresholdRule> sharedRules = new ArrayList<>();

        for (String[] f : readRows(resource, 8)) {
            String device = f[4].equals(NO_DEVICE) ? null : f[4];
            if (device != null && !devices.contains(device)) {
                throw new IllegalArgumentException(resource + ": unknown device '" + device + "'");
            }
            ThresholdRule rule = new ThresholdRule(
                    SensorType.valueOf(f[1]), ThresholdRule.Direction.valueOf(f[2]), Double.parseDouble(f[3]),
                    device, Integer.parseInt(f[5]), Integer.parseInt(f[6]), f[7]);

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

    /** chamber, crop */
    public List<ChamberAssignment> loadChambers(String resource) {
        List<ChamberAssignment> chambers = new ArrayList<>();
        for (String[] f : readRows(resource, 2)) {
            chambers.add(new ChamberAssignment(f[0], f[1]));
        }
        return chambers;
    }

    private List<String[]> readRows(String resource, int columns) {
        InputStream in = FarmConfigLoader.class.getResourceAsStream("/" + resource);
        if (in == null) {
            throw new IllegalStateException("Missing resource: " + resource);
        }
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank() || line.strip().startsWith("#")) {
                    continue;
                }
                String[] fields = line.split(",");
                if (fields.length != columns) {
                    throw new IllegalArgumentException(resource + " line " + lineNo
                            + ": expected " + columns + " columns but found " + fields.length);
                }
                for (int i = 0; i < fields.length; i++) {
                    fields[i] = fields[i].strip();
                }
                rows.add(fields);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }
}
