package org.example.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.example.metrics.Resource;

/**
 * Consumption of one resource, in total and per chamber. One parameterised class serves the
 * water, energy and nutrient reports instead of three near-identical copies (DRY).
 */
public final class ResourceUsageReport implements Report {

    private final Resource resource;
    private final String title;

    public ResourceUsageReport(Resource resource, String title) {
        this.resource = Objects.requireNonNull(resource, "resource");
        this.title = Objects.requireNonNull(title, "title");
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public List<String> lines(FarmSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        lines.add("Total " + resource.label() + " Usage: " + format(snapshot.totalUsage(resource)));
        snapshot.usage(resource).forEach((chamber, amount) -> lines.add(chamber + ": " + format(amount)));
        return lines;
    }

    private String format(long amount) {
        return amount + " " + resource.unit();
    }
}
