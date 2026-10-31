package org.example.report;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.example.sensor.UsageMeter;

/**
 * Water and energy used, per chamber. AtomicInteger makes each "+=" a single
 * atomic step and computeIfAbsent creates a chamber's counters atomically, so
 * concurrent updates are never lost (legacy issue C3). The totals are the sum
 * over chambers, so the daily report and the usage report read the same counters.
 *
 * Chamber code charges it as a UsageMeter; the reports read it as UsageTotals.
 */
public class ResourceMeter implements UsageMeter, UsageTotals {

    private record Counters(AtomicInteger water, AtomicInteger energy) {
    }

    private final ConcurrentMap<String, Counters> counters = new ConcurrentHashMap<>();

    @Override
    public void addWater(String chamberId, int litres) {
        countersFor(chamberId).water().addAndGet(litres);
    }

    @Override
    public void addEnergy(String chamberId, int kwh) {
        countersFor(chamberId).energy().addAndGet(kwh);
    }

    @Override
    public int water() {
        return counters.values().stream().mapToInt(c -> c.water().get()).sum();
    }

    @Override
    public int energy() {
        return counters.values().stream().mapToInt(c -> c.energy().get()).sum();
    }

    @Override
    public SortedMap<String, Usage> usageByChamber() {
        SortedMap<String, Usage> usage = new TreeMap<>();
        for (Map.Entry<String, Counters> entry : counters.entrySet()) {
            Counters c = entry.getValue();
            usage.put(entry.getKey(), new Usage(c.water().get(), c.energy().get()));
        }
        return usage;
    }

    private Counters countersFor(String chamberId) {
        return counters.computeIfAbsent(chamberId, id -> new Counters(new AtomicInteger(), new AtomicInteger()));
    }
}
