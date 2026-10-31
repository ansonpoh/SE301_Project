package harness;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Random;

import org.example.SVFRMS;
import org.example.sensor.RandomSensorSource;

/**
 * Runs the REFACTORED system the same way BaselineHarness runs the legacy one:
 * seeded Random, chambers one at a time (maxChamberThreads = 1), control thread
 * running concurrently. Its normalized output must match
 * baseline/expected/seed<N>-normalized.txt.
 */
public class RefactoredHarness {
    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        if (args.length < 2) {
            boolean ok = SVFRMS.createSimulation(new RandomSensorSource(new Random(seed)), 1, System.out)
                    .run(SVFRMS.REPORT_FILE);
            if (!ok) {
                System.exit(1);
            }
            return;
        }
        // With a config dir: its "Using <file> instead of the built-in ..." notice goes to
        // err.txt, not stdout, so it cannot break the diff; anything else there is a failure.
        Path errFile = Path.of("err.txt");
        boolean ok;
        try (PrintStream err = new PrintStream(new FileOutputStream(errFile.toFile()), true, "UTF-8")) {
            ok = SVFRMS.createSimulation(new RandomSensorSource(new Random(seed)), 1, Path.of(args[1]),
                    System.out, err, Clock.systemDefaultZone()).run(SVFRMS.REPORT_FILE);
        }
        for (String line : Files.readAllLines(errFile)) {
            if (!line.isBlank() && !(line.startsWith("Using ") && line.contains(" instead of the built-in "))) {
                System.err.println("Unexpected error output: " + line);
                System.exit(2);
            }
        }
        if (!ok) {
            System.exit(1);
        }
    }
}
