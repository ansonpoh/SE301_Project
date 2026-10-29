package org.example.crop;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.config.CsvFile;
import org.example.model.CropProfile;
import org.example.model.Range;
import org.example.model.SensorType;

/**
 * Crop profiles defined as rows of {@code name, min, max, min, max, ...}, with one min/max pair
 * per {@link SensorType} in declaration order.
 * <p>
 * Adding or removing a crop is a data change only: no Java code has to be modified (OCP).
 */
public final class CsvCropRepository implements CropRepository {

    public static final String DEFAULT_FILE = "crops.csv";

    private static final int EXPECTED_COLUMNS = 1 + 2 * SensorType.values().length;

    private final Map<String, CropProfile> cropsByName;

    public CsvCropRepository(List<List<String>> rows) {
        Map<String, CropProfile> crops = new LinkedHashMap<>();
        for (List<String> row : rows) {
            CropProfile crop = parse(row);
            if (crops.putIfAbsent(key(crop.name()), crop) != null) {
                throw new IllegalArgumentException("Duplicate crop profile: " + crop.name());
            }
        }
        this.cropsByName = Collections.unmodifiableMap(crops);
    }

    public static CsvCropRepository fromFile(String fileName) {
        return new CsvCropRepository(CsvFile.readRows(fileName));
    }

    @Override
    public Optional<CropProfile> findByName(String name) {
        return Optional.ofNullable(cropsByName.get(key(name)));
    }

    @Override
    public List<CropProfile> findAll() {
        return List.copyOf(cropsByName.values());
    }

    private static CropProfile parse(List<String> row) {
        if (row.size() != EXPECTED_COLUMNS) {
            throw new IllegalArgumentException(
                    "Crop row needs " + EXPECTED_COLUMNS + " columns but has " + row.size() + ": " + row);
        }
        Map<SensorType, Range> ranges = new EnumMap<>(SensorType.class);
        SensorType[] types = SensorType.values();
        for (int i = 0; i < types.length; i++) {
            double min = parseNumber(row, 1 + 2 * i);
            double max = parseNumber(row, 2 + 2 * i);
            ranges.put(types[i], new Range(min, max));
        }
        return new CropProfile(row.get(0), ranges);
    }

    private static double parseNumber(List<String> row, int column) {
        try {
            return Double.parseDouble(row.get(column));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number '" + row.get(column) + "' in crop row " + row, e);
        }
    }

    private static String key(String name) {
        return name.trim().toLowerCase();
    }
}
