package org.example.crop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.example.TestFixtures;
import org.example.model.CropProfile;
import org.example.model.Range;
import org.example.model.SensorType;
import org.junit.jupiter.api.Test;

class CsvCropRepositoryTest {

    @Test
    void parsesRangesInSensorOrder() {
        CropProfile tomato = TestFixtures.tomato();

        assertEquals(new Range(22, 28), tomato.range(SensorType.TEMPERATURE));
        assertEquals(new Range(60, 80), tomato.range(SensorType.HUMIDITY));
        assertEquals(new Range(45, 60), tomato.range(SensorType.SOIL_MOISTURE));
        assertEquals(new Range(500, 1000), tomato.range(SensorType.LIGHT));
        assertEquals(new Range(40, 90), tomato.range(SensorType.NUTRIENT));
    }

    @Test
    void newCropIsJustAnotherRow() {
        List<String> kale = List.of("Kale", "15", "24", "60", "80", "55", "75", "400", "850", "40", "85");
        CropRepository repository = new CsvCropRepository(List.of(TestFixtures.TOMATO_ROW, kale));

        assertEquals(2, repository.findAll().size());
        assertTrue(repository.findByName("kale").isPresent(), "lookup is case-insensitive");
    }

    @Test
    void bundledFileContainsSpecCropsAndExtensions() {
        CropRepository repository = CsvCropRepository.fromFile(CsvCropRepository.DEFAULT_FILE);

        for (String crop : List.of("Tomato", "Strawberry", "Lettuce", "Kale", "Bell Pepper", "Bok Choy", "Eggplant")) {
            assertTrue(repository.findByName(crop).isPresent(), crop + " should be configured");
        }
    }

    @Test
    void rejectsRowWithMissingColumns() {
        assertThrows(IllegalArgumentException.class,
                () -> new CsvCropRepository(List.of(List.of("Broken", "1", "2"))));
    }

    @Test
    void rejectsDuplicateCrop() {
        assertThrows(IllegalArgumentException.class,
                () -> new CsvCropRepository(List.of(TestFixtures.TOMATO_ROW, TestFixtures.TOMATO_ROW)));
    }
}
