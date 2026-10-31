package org.example.sensor;

/**
 * Where water and energy use is charged (Dependency Inversion): chamber
 * monitors and the sensor processor only add to it; only the reports read
 * the totals, from the ResourceMeter that implements it. It lives here, with
 * the code that calls it (like ReadingLog), so the report package depends on
 * sensor and never the other way round.
 *
 * Each charge names its chamber, so use can be reported per chamber (spec 3.5).
 */
public interface UsageMeter {
    void addWater(String chamberId, int litres);

    void addEnergy(String chamberId, int kwh);
}
