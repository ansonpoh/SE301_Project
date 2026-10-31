package org.example.report;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Timestamped log of every reading processed. Kept from the legacy code
 * (which collected it but never printed it). Thread-safe list.
 */
public class SensorLog {
    private final List<String> entries = Collections.synchronizedList(new ArrayList<>());
    private final Clock clock;

    public SensorLog(Clock clock) {
        this.clock = clock;
    }

    public void record(String message) {
        entries.add(Timestamps.now(clock) + " : " + message);
    }

    public int size() {
        return entries.size();
    }
}
