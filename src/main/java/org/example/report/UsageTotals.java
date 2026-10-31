package org.example.report;

import java.util.SortedMap;

/**
 * The read side of water and energy metering (Interface Segregation, Dependency
 * Inversion): the reports read totals through this, while chamber code only
 * charges through UsageMeter. ResourceMeter implements both, so neither side
 * depends on the other's methods or on the concrete class.
 */
public interface UsageTotals {

    /** One chamber's use. */
    record Usage(int water, int energy) {
    }

    /** Litres used by every chamber together. */
    int water();

    /** kWh used by every chamber together. */
    int energy();

    /** Each chamber's use, sorted by chamber id. */
    SortedMap<String, Usage> usageByChamber();
}
