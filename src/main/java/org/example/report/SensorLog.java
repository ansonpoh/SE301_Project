package org.example.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Timestamped log of every reading processed. Kept from the legacy code
 * (which collected it but never printed it). Thread-safe list.
 */
public class SensorLog {
    private final List<String> entries = Collections.synchronizedList(new ArrayList<>());

    public void record(String message) {
        entries.add(new Date() + " : " + message);
    }

    public int size() {
        return entries.size();
    }
}
