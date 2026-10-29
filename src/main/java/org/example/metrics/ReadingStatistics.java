package org.example.metrics;

import java.util.concurrent.atomic.AtomicLong;

import org.example.model.SensorReading;

/**
 * Counts processed and failed sensor readings.
 * <p>
 * The legacy system appended a log string for every reading to an unsynchronised
 * {@code ArrayList} that nothing ever read; concurrent {@code add} calls corrupted it and threw
 * {@code ArrayIndexOutOfBoundsException}. Only the counts were ever useful, so atomic counters
 * replace the unbounded list.
 */
public final class ReadingStatistics {

    private final AtomicLong total = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public void onReading(SensorReading reading) {
        total.incrementAndGet();
        if (!reading.isValid()) {
            failed.incrementAndGet();
        }
    }

    public long total() {
        return total.get();
    }

    public long failed() {
        return failed.get();
    }
}
