package org.example.report;

/**
 * Where water and energy use is charged (Dependency Inversion): chamber
 * monitors and the sensor processor only add to it; only the report reads
 * the totals, from the ResourceMeter that implements it.
 */
public interface UsageMeter {
    void addWater(int litres);

    void addEnergy(int kwh);
}
