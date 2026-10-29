package org.example.report;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import org.example.alert.Alert;
import org.example.metrics.Resource;

/**
 * Immutable picture of the whole facility, taken once and shared by every report.
 * Reports read only this snapshot, never live (concurrently changing) state.
 */
public record FarmSnapshot(Instant generatedAt,
                           List<ChamberSnapshot> chambers,
                           long alertsGenerated,
                           int activeAlerts,
                           List<Alert> archivedAlerts,
                           long sensorReadings,
                           long failedReadings,
                           Map<Resource, SortedMap<String, Long>> usageByResource) {

    public FarmSnapshot {
        chambers = List.copyOf(chambers);
        archivedAlerts = List.copyOf(archivedAlerts);
        usageByResource = Map.copyOf(usageByResource);
    }

    public SortedMap<String, Long> usage(Resource resource) {
        return usageByResource.get(resource);
    }

    public long totalUsage(Resource resource) {
        return usage(resource).values().stream().mapToLong(Long::longValue).sum();
    }
}
