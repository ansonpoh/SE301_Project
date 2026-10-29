package org.example.report;

import java.util.Map;

import org.example.metrics.ChamberActivity;
import org.example.model.SensorType;

/** Point-in-time view of one chamber for reporting. Immutable. */
public record ChamberSnapshot(String chamberId, String cropName, Map<SensorType, Double> environment,
                              ChamberActivity activity) {

    public ChamberSnapshot {
        environment = Map.copyOf(environment);
    }
}
