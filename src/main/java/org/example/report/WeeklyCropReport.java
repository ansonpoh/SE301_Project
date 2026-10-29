package org.example.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.example.metrics.ChamberActivity;

/** Alerts and mitigation actions per crop, followed by the per-chamber summary. */
public final class WeeklyCropReport implements Report {

    @Override
    public String title() {
        return "WEEKLY CROP REPORT";
    }

    @Override
    public List<String> lines(FarmSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        Map<String, List<ChamberSnapshot>> byCrop = snapshot.chambers().stream()
                .collect(Collectors.groupingBy(ChamberSnapshot::cropName, TreeMap::new, Collectors.toList()));
        byCrop.forEach((crop, chambers) -> lines.add(crop
                + " -> Chambers=" + chambers.size()
                + ", Alerts=" + sum(chambers, ChamberActivity::alerts)
                + ", Actions=" + sum(chambers, ChamberActivity::actions)));

        lines.add("--- chamber wise summary ---");
        for (ChamberSnapshot chamber : snapshot.chambers()) {
            ChamberActivity activity = chamber.activity();
            lines.add(chamber.chamberId() + " -> Alerts=" + activity.alerts()
                    + ", Actions=" + activity.actions()
                    + ", Last Action=" + activity.lastAction()
                    + ", Last Alert=" + activity.lastAlert());
        }
        return lines;
    }

    private static int sum(List<ChamberSnapshot> chambers, ToIntFunction<ChamberActivity> field) {
        return chambers.stream().map(ChamberSnapshot::activity).mapToInt(field).sum();
    }
}
