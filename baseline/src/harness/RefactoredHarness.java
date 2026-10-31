package harness;

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
        boolean ok = SVFRMS.createSimulation(new RandomSensorSource(new Random(seed)), 1, System.out)
                .run(SVFRMS.REPORT_FILE);
        if (!ok) {
            System.exit(1);
        }
    }
}
