package org.example.metrics;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Accumulates resource consumption per chamber.
 * <p>
 * Replaces the legacy {@code totalWaterUsage += ...} on shared static ints, which lost updates
 * because {@code +=} is a non-atomic read-modify-write. Counters live in a
 * {@link ConcurrentHashMap} and are created with {@code computeIfAbsent} (no check-then-act race)
 * and incremented with {@link AtomicLong} (no lost updates).
 */
public final class ResourceMeter {

    private record Key(String chamberId, Resource resource) {
    }

    private final Map<Key, AtomicLong> counters = new ConcurrentHashMap<>();

    public void record(String chamberId, ResourceUsage usage) {
        counters.computeIfAbsent(new Key(chamberId, usage.resource()), key -> new AtomicLong())
                .addAndGet(usage.amount());
    }

    public long total(Resource resource) {
        return counters.entrySet().stream()
                .filter(entry -> entry.getKey().resource() == resource)
                .mapToLong(entry -> entry.getValue().get())
                .sum();
    }

    /** Usage of one resource broken down by chamber, sorted by chamber id. */
    public SortedMap<String, Long> byChamber(Resource resource) {
        SortedMap<String, Long> result = new TreeMap<>();
        counters.forEach((key, counter) -> {
            if (key.resource() == resource) {
                result.merge(key.chamberId(), counter.get(), Long::sum);
            }
        });
        return result;
    }
}
