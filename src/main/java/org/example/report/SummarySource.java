package org.example.report;

import java.util.SortedMap;

import org.example.model.ChamberSummary;

/**
 * The read side of the per-chamber alert and action counts (Interface
 * Segregation, Dependency Inversion): the reports read them through this, not
 * through ChamberSummaryTracker, which also listens to alerts and actions.
 */
public interface SummarySource {

    /** A consistent snapshot of each chamber's summary, sorted by chamber id. */
    SortedMap<String, ChamberSummary.Snapshot> sortedSummaries();
}
