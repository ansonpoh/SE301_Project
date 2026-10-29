package org.example;

import java.util.Random;

/**
 * Baseline harness for the UNMODIFIED legacy SVFRMS class.
 *
 * The legacy main() shares one Random between four chamber threads, so even a
 * fixed seed gives a different reading sequence per chamber on every run. To get
 * a reproducible baseline we:
 *   1. replace SVFRMS.random with a seeded Random, and
 *   2. run the same ChamberThread objects one after another (start + join)
 *      instead of all at once, so the random draws always happen in the same order.
 * Everything else (ControlThread, archiveAlerts, generateDailyReport,
 * saveArchiveReport) is the legacy code, called in the order main() uses.
 *
 * archiveAlerts() runs before generateDailyReport(): in the legacy code these
 * two threads race, and archive-first is the order seen in ~96% of runs.
 *
 * Note: CONTROL log lines (and their "reduced to 19.0C" values) still depend on
 * how far the control thread lags behind the chamber thread, so they are
 * compared as counts, not line by line (see normalize.py).
 */
public class BaselineHarness {
    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        SVFRMS.random = new Random(seed);

        Thread control = new SVFRMS.ControlThread();
        control.start();

        String[][] chambers = {{"CH1", "Tomato"}, {"CH2", "Strawberry"}, {"CH3", "Lettuce"}, {"CH4", "Chili"}};
        for (String[] c : chambers) {
            Thread t = new SVFRMS.ChamberThread(c[0], c[1]);
            t.start();
            t.join();
        }

        synchronized (SVFRMS.controlQueue) {
            SVFRMS.controlThreadRunning = false;
            SVFRMS.controlQueue.notifyAll();
        }
        control.join();

        SVFRMS.archiveAlerts();
        SVFRMS.generateDailyReport();
        System.out.println("System finished sensor processing.");
        SVFRMS.saveArchiveReport();
    }
}
