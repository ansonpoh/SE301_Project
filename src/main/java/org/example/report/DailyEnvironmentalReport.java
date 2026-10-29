package org.example.report;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.example.model.SensorType;

/** Alert totals and the latest environmental conditions in every chamber. */
public final class DailyEnvironmentalReport implements Report {

    @Override
    public String title() {
        return "DAILY ENVIRONMENTAL REPORT";
    }

    @Override
    public List<String> lines(FarmSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        lines.add("Generated Alerts: " + snapshot.alertsGenerated());
        lines.add("Active Alerts: " + snapshot.activeAlerts());
        lines.add("Archived Alerts: " + snapshot.archivedAlerts().size());
        lines.add("Sensor Readings: " + snapshot.sensorReadings() + " (" + snapshot.failedReadings() + " failed)");
        for (ChamberSnapshot chamber : snapshot.chambers()) {
            lines.add(chamber.chamberId() + " [" + chamber.cropName() + "] " + describe(chamber));
        }
        return lines;
    }

    private static String describe(ChamberSnapshot chamber) {
        return Arrays.stream(SensorType.values())
                .map(type -> type.label() + "=" + type.format(chamber.environment().get(type)))
                .collect(Collectors.joining(", "));
    }
}
