package org.example.report;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Total water and energy used. AtomicInteger makes each "+=" a single atomic
 * step, so concurrent updates are never lost (legacy issue C3).
 */
public class ResourceMeter implements UsageMeter {
    private final AtomicInteger waterLitres = new AtomicInteger();
    private final AtomicInteger energyKwh = new AtomicInteger();

    @Override
    public void addWater(int litres) {
        waterLitres.addAndGet(litres);
    }

    @Override
    public void addEnergy(int kwh) {
        energyKwh.addAndGet(kwh);
    }

    public int water() {
        return waterLitres.get();
    }

    public int energy() {
        return energyKwh.get();
    }
}
