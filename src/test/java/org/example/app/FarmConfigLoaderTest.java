package org.example.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.example.alert.Severity;
import org.example.crop.CropRegistry;
import org.example.crop.ThresholdRule;
import org.example.device.CoolingFan;
import org.example.device.Dehumidifier;
import org.example.device.DeviceRegistry;
import org.example.device.GrowLight;
import org.example.device.HeatingUnit;
import org.example.device.Humidifier;
import org.example.device.NutrientInjector;
import org.example.device.ShadeController;
import org.example.device.VentilationSystem;
import org.example.device.WaterPump;
import org.example.model.SensorType;
import org.example.sensor.SensorRanges;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FarmConfigLoaderTest {
    private static final List<ChamberAssignment> DEFAULT_CHAMBERS = List.of(
            new ChamberAssignment("CH1", "Tomato"),
            new ChamberAssignment("CH2", "Strawberry"),
            new ChamberAssignment("CH3", "Lettuce"),
            new ChamberAssignment("CH4", "Chili"));

    private final FarmConfigLoader loader = new FarmConfigLoader();
    private final DeviceRegistry devices = new DeviceRegistry()
            .register(new CoolingFan()).register(new WaterPump()).register(new NutrientInjector())
            .register(new Humidifier()).register(new Dehumidifier()).register(new GrowLight())
            .register(new HeatingUnit()).register(new VentilationSystem()).register(new ShadeController());

    @TempDir
    Path dir;

    private CropRegistry builtInCrops() {
        return loader.loadCrops("crops.csv", devices);
    }

    @Test
    void loadsTheLegacyCropsAndTheNewOnes() {
        CropRegistry crops = builtInCrops();

        for (String crop : List.of("Tomato", "Strawberry", "Lettuce", "Spinach", "Chili", "Cucumber",
                "Kale", "Bell Pepper", "Bok Choy", "Eggplant")) {
            assertTrue(crops.contains(crop), crop);
        }

        List<ThresholdRule> tomatoTemp = crops.rulesFor("Tomato", SensorType.TEMPERATURE);
        assertEquals(2, tomatoTemp.size());
        assertEquals(28, tomatoTemp.get(0).limit());
        assertEquals("Cooling Fan", tomatoTemp.get(0).deviceName());
        assertEquals(10, tomatoTemp.get(0).energyCost());
        assertEquals(Severity.CRITICAL, tomatoTemp.get(0).severity());
        assertEquals(Severity.INFO, crops.rulesFor("Tomato", SensorType.LIGHT).get(0).severity());
    }

    @Test
    void newCropUsesTheSameRuleEngineWithNoCodeChange() {
        List<ThresholdRule> kaleSoil = builtInCrops().rulesFor("Kale", SensorType.SOIL);
        assertEquals(1, kaleSoil.size());
        assertTrue(kaleSoil.get(0).isBreachedBy(39));
        assertEquals(30, kaleSoil.get(0).waterCost());
        assertEquals("CH5 Kale Low Soil Moisture", kaleSoil.get(0).alertText("CH5", "Kale"));
    }

    @Test
    void defaultChambersAreTheLegacyFour() {
        assertEquals(DEFAULT_CHAMBERS, loader.loadChambers("chambers.csv", builtInCrops()));
    }

    @Test
    void rejectsUnknownDevice() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> loader.loadCrops("test-unknown-device.csv", devices));
        assertTrue(e.getMessage().contains("line 1") && e.getMessage().contains("Misting Nozzle"), e.getMessage());
    }

    @Test
    void rejectsWrongColumnCount() {
        assertThrows(IllegalArgumentException.class, () -> loader.loadCrops("test-bad-columns.csv", devices));
    }

    /**
     * A crop with no rows runs with only the shared rules, as in the legacy code,
     * but a typo or a crop removed from crops.csv is reported at start-up.
     */
    @Test
    void warnsAboutChamberWithUnknownCrop() throws IOException {
        Files.writeString(dir.resolve("chambers.csv"), "CH1, Tomato\nCH5, Kael\n");
        ByteArrayOutputStream notices = new ByteArrayOutputStream();
        FarmConfigLoader fromDir = new FarmConfigLoader(dir, new PrintStream(notices, true, StandardCharsets.UTF_8));

        List<ChamberAssignment> chambers = fromDir.loadChambers("chambers.csv", builtInCrops());

        assertEquals(List.of(new ChamberAssignment("CH1", "Tomato"), new ChamberAssignment("CH5", "Kael")), chambers);
        String text = notices.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("WARNING") && text.contains("line 2") && text.contains("Kael"), text);
    }

    @Test
    void loadsSensorRanges() {
        SensorRanges ranges = loader.loadSensorRanges("sensors.csv");

        assertTrue(ranges.isValid(SensorType.HUMIDITY, 0) && ranges.isValid(SensorType.HUMIDITY, 100));
        assertFalse(ranges.isValid(SensorType.HUMIDITY, -1) || ranges.isValid(SensorType.HUMIDITY, 101));
        assertTrue(ranges.isValid(SensorType.TEMPERATURE, -20));
        assertFalse(ranges.isValid(SensorType.SOIL, Double.NaN));
    }

    /** A sensor with no range would make every reading of it fail, so it is rejected at start-up. */
    @Test
    void rejectsSensorRangesWithAMissingSensor() throws IOException {
        Files.writeString(dir.resolve("sensors.csv"),
                "TEMPERATURE, -20, 60\nHUMIDITY, 0, 100\nSOIL, 0, 100\nLIGHT, 0, 200000\n");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new FarmConfigLoader(dir, null).loadSensorRanges("sensors.csv"));
        assertTrue(e.getMessage().contains("sensors.csv") && e.getMessage().contains("NUTRIENT"), e.getMessage());
    }

    @Test
    void rejectsSensorRangeWithMinAboveMax() throws IOException {
        Files.writeString(dir.resolve("sensors.csv"), "TEMPERATURE, 60, -20\n");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new FarmConfigLoader(dir, null).loadSensorRanges("sensors.csv"));
        assertTrue(e.getMessage().contains("line 1"), e.getMessage());
    }

    @Test
    void rejectsChambersFileWithNoChambers() throws IOException {
        Files.writeString(dir.resolve("chambers.csv"), "# CH1, Tomato\n");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new FarmConfigLoader(dir, null).loadChambers("chambers.csv", builtInCrops()));
        assertTrue(e.getMessage().contains("no chambers"), e.getMessage());
    }

    @Test
    void rejectsDuplicateChamber() throws IOException {
        Files.writeString(dir.resolve("chambers.csv"), "CH1, Tomato\nCH1, Kale\n");

        assertThrows(IllegalArgumentException.class,
                () -> new FarmConfigLoader(dir, null).loadChambers("chambers.csv", builtInCrops()));
    }

    @Test
    void configFolderOverridesOnlyTheFilesItContainsAndSaysSo() throws IOException {
        Files.writeString(dir.resolve("chambers.csv"), "CH1, Tomato\nCH5, Kale\n");
        ByteArrayOutputStream notices = new ByteArrayOutputStream();
        FarmConfigLoader fromDir = new FarmConfigLoader(dir, new PrintStream(notices, true, StandardCharsets.UTF_8));

        CropRegistry crops = fromDir.loadCrops("crops.csv", devices);           // not in dir: built-in
        List<ChamberAssignment> chambers = fromDir.loadChambers("chambers.csv", crops);

        assertEquals(List.of(new ChamberAssignment("CH1", "Tomato"), new ChamberAssignment("CH5", "Kale")), chambers);
        String text = notices.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("chambers.csv"), text);
        assertFalse(text.contains("crops.csv"), "no notice for a file that was not overridden: " + text);
    }

    /** Windows editors add a BOM; PowerShell 5.1 saves and appends with ">>" as UTF-16. */
    @Test
    void readsFilesSavedByWindowsTools() throws IOException {
        Path bom = dir.resolve("bom");
        Files.createDirectories(bom);
        Files.write(bom.resolve("chambers.csv"), concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                "# comment\r\nCH1, Tomato\r\n".getBytes(StandardCharsets.UTF_8)));

        Path utf16 = dir.resolve("utf16");
        Files.createDirectories(utf16);
        Files.write(utf16.resolve("chambers.csv"), concat(new byte[] {(byte) 0xFF, (byte) 0xFE},
                "# comment\r\nCH1, Tomato\r\n".getBytes(StandardCharsets.UTF_16LE)));

        Path appended = dir.resolve("appended");
        Files.createDirectories(appended);
        Path file = appended.resolve("chambers.csv");
        Files.writeString(file, "# comment\nCH1, Tomato\n");
        Files.write(file, concat(new byte[] {(byte) 0xFF, (byte) 0xFE},
                "CH5, Kale\r\n".getBytes(StandardCharsets.UTF_16LE)), StandardOpenOption.APPEND);

        CropRegistry crops = builtInCrops();
        ChamberAssignment ch1 = new ChamberAssignment("CH1", "Tomato");
        assertEquals(List.of(ch1), new FarmConfigLoader(bom, null).loadChambers("chambers.csv", crops));
        assertEquals(List.of(ch1), new FarmConfigLoader(utf16, null).loadChambers("chambers.csv", crops));
        assertEquals(List.of(ch1, new ChamberAssignment("CH5", "Kale")),
                new FarmConfigLoader(appended, null).loadChambers("chambers.csv", crops));
    }

    /** A file saved as ANSI (here "Pak Choï" in Windows-1252) must fail clearly, not lose the "ï". */
    @Test
    void rejectsTextThatIsNotUtf8() throws IOException {
        Files.write(dir.resolve("chambers.csv"), "CH1, Pak Choï\n".getBytes("windows-1252"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new FarmConfigLoader(dir, null).loadChambers("chambers.csv", builtInCrops()));
        assertTrue(e.getMessage().contains("chambers.csv") && e.getMessage().contains("UTF-8"), e.getMessage());
    }

    /** A lone 0xFF is not a BOM: an ANSI "ÿ" must fail like any other non-UTF-8 byte, not vanish. */
    @Test
    void keepsLoneFfByteSoItIsRejected() throws IOException {
        Files.write(dir.resolve("chambers.csv"), "CH1, Tomatoÿ\n".getBytes("windows-1252"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new FarmConfigLoader(dir, null).loadChambers("chambers.csv", builtInCrops()));
        assertTrue(e.getMessage().contains("UTF-8"), e.getMessage());
    }

    /** A limit that can never be breached, or a negative cost, would fail silently at run time. */
    @Test
    void rejectsNanLimitAndNegativeCost() throws IOException {
        Files.writeString(dir.resolve("crops.csv"),
                "Kale, TEMPERATURE, HIGH, NaN, Cooling Fan, 0, 0, CRITICAL, {crop} High Temp\n");
        IllegalArgumentException nan = assertThrows(IllegalArgumentException.class,
                () -> new FarmConfigLoader(dir, null).loadCrops("crops.csv", devices));
        assertTrue(nan.getMessage().contains("line 1") && nan.getMessage().contains("limit"), nan.getMessage());

        Files.writeString(dir.resolve("crops.csv"),
                "Kale, SOIL, LOW, 40, Water Pump, -30, 0, WARNING, {crop} Low Soil Moisture\n");
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> new FarmConfigLoader(dir, null).loadCrops("crops.csv", devices));
        assertTrue(negative.getMessage().contains("line 1") && negative.getMessage().contains("negative"),
                negative.getMessage());
    }

    /** A copied row with only new text would still double that crop's alerts, costs and device requests. */
    @Test
    void rejectsSecondRuleWithTheSameTrigger() throws IOException {
        Files.writeString(dir.resolve("crops.csv"),
                "Kale, TEMPERATURE, HIGH, 24, Cooling Fan, 0, 8, CRITICAL, {crop} High Temp\n"
                        + "# comment\n"
                        + "Kale, TEMPERATURE, HIGH, 24, Cooling Fan, 0, 8, CRITICAL, {crop} Too Hot\n");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new FarmConfigLoader(dir, null).loadCrops("crops.csv", devices));
        assertTrue(e.getMessage().contains("line 3") && e.getMessage().contains("line 1"), e.getMessage());
    }

    /** A crop row that repeats a shared "*" row would fire twice for that crop, even if the shared row comes later. */
    @Test
    void rejectsCropRuleThatRepeatsASharedRule() throws IOException {
        Files.writeString(dir.resolve("crops.csv"),
                "Kale, LIGHT, LOW, 500, Grow Light, 0, 0, INFO, {crop} Dim\n"
                        + "*, LIGHT, LOW, 500, Grow Light, 0, 0, INFO, Low Light Level\n");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new FarmConfigLoader(dir, null).loadCrops("crops.csv", devices));
        assertTrue(e.getMessage().contains("line 1") && e.getMessage().contains("shared rule on line 2"),
                e.getMessage());
    }

    /** The message is the last column, so commas in it are part of the text, not extra columns. */
    @Test
    void allowsCommasInTheMessage() throws IOException {
        Files.writeString(dir.resolve("crops.csv"),
                "Kale, TEMPERATURE, HIGH, 24, Cooling Fan, 0, 8, CRITICAL, {crop} High Temp, check fan\n");

        ThresholdRule rule = new FarmConfigLoader(dir, null).loadCrops("crops.csv", devices)
                .rulesFor("Kale", SensorType.TEMPERATURE).get(0);
        assertEquals("CH5 Kale High Temp, check fan", rule.alertText("CH5", "Kale"));
    }

    @Test
    void newCropsUseTheSpecDevices() {
        CropRegistry crops = builtInCrops();
        assertEquals("Ventilation System", crops.rulesFor("Bell Pepper", SensorType.HUMIDITY).get(0).deviceName());
        assertEquals("Shade Controller", crops.rulesFor("Bell Pepper", SensorType.LIGHT).get(0).deviceName());
        assertEquals("Heating Unit", crops.rulesFor("Eggplant", SensorType.TEMPERATURE).get(1).deviceName());
    }

    /** Different limits on the same sensor are separate levels, not duplicates. */
    @Test
    void allowsRulesAtDifferentLimits() throws IOException {
        Files.writeString(dir.resolve("crops.csv"),
                "Kale, TEMPERATURE, HIGH, 24, Cooling Fan, 0, 8, WARNING, {crop} Warm\n"
                        + "Kale, TEMPERATURE, HIGH, 30, Cooling Fan, 0, 8, CRITICAL, {crop} Hot\n");

        assertEquals(2, new FarmConfigLoader(dir, null).loadCrops("crops.csv", devices)
                .rulesFor("Kale", SensorType.TEMPERATURE).size());
    }

    @Test
    void rejectsEmptyColumn() throws IOException {
        Files.writeString(dir.resolve("chambers.csv"), "CH1, \n");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new FarmConfigLoader(dir, null).loadChambers("chambers.csv", builtInCrops()));
        assertTrue(e.getMessage().contains("line 1") && e.getMessage().contains("column 2"), e.getMessage());
    }

    /** Appended UTF-16 text is decoded as UTF-16, so a non-ASCII crop name survives intact. */
    @Test
    void decodesAppendedUtf16TextInItsOwnEncoding() throws IOException {
        Path file = dir.resolve("chambers.csv");
        Files.writeString(file, "CH1, Tomato\n");
        Files.write(file, concat(new byte[] {(byte) 0xFF, (byte) 0xFE},
                "CH5, Pak Choï\r\n".getBytes(StandardCharsets.UTF_16LE)), StandardOpenOption.APPEND);

        assertEquals(List.of(new ChamberAssignment("CH1", "Tomato"), new ChamberAssignment("CH5", "Pak Choï")),
                new FarmConfigLoader(dir, null).loadChambers("chambers.csv", builtInCrops()));
    }

    /** A trailing comma is an extra (empty) column, not something to ignore. */
    @Test
    void countsTrailingEmptyColumns() throws IOException {
        Files.writeString(dir.resolve("chambers.csv"), "CH1, Tomato,\n");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new FarmConfigLoader(dir, null).loadChambers("chambers.csv", builtInCrops()));
        assertTrue(e.getMessage().contains("line 1") && e.getMessage().contains("found 3"), e.getMessage());
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
