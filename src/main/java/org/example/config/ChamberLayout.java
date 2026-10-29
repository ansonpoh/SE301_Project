package org.example.config;

import java.util.List;

import org.example.crop.CropRepository;
import org.example.model.Chamber;
import org.example.model.ChamberState;

/**
 * Builds the facility's chambers from rows of {@code chamberId, cropName}.
 */
public final class ChamberLayout {

    public static final String DEFAULT_FILE = "chambers.csv";

    private ChamberLayout() {
    }

    public static List<Chamber> fromFile(String fileName, CropRepository crops) {
        return fromRows(CsvFile.readRows(fileName), crops);
    }

    public static List<Chamber> fromRows(List<List<String>> rows, CropRepository crops) {
        return rows.stream()
                .map(row -> toChamber(row, crops))
                .toList();
    }

    private static Chamber toChamber(List<String> row, CropRepository crops) {
        if (row.size() != 2) {
            throw new IllegalArgumentException("Chamber row needs 2 columns (chamber, crop): " + row);
        }
        String cropName = row.get(1);
        return crops.findByName(cropName)
                .map(crop -> new Chamber(row.get(0), crop, ChamberState.withDefaults()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown crop '" + cropName + "' for chamber " + row.get(0)));
    }
}
