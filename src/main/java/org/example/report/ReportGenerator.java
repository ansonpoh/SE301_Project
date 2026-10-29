package org.example.report;

import java.util.ArrayList;
import java.util.List;

import org.example.metrics.Resource;

/** Renders a set of reports, each under a common heading format. */
public final class ReportGenerator {

    private final List<Report> reports;

    public ReportGenerator(List<Report> reports) {
        this.reports = List.copyOf(reports);
    }

    /** The five reports required by the specification. */
    public static ReportGenerator standard() {
        return new ReportGenerator(List.of(
                new DailyEnvironmentalReport(),
                new WeeklyCropReport(),
                new ResourceUsageReport(Resource.WATER, "WATER USAGE REPORT"),
                new ResourceUsageReport(Resource.ENERGY, "ENERGY CONSUMPTION REPORT"),
                new ResourceUsageReport(Resource.NUTRIENT, "NUTRIENT CONSUMPTION REPORT")));
    }

    public List<String> render(FarmSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        for (Report report : reports) {
            lines.add("===== " + report.title() + " =====");
            lines.addAll(report.lines(snapshot));
        }
        return lines;
    }
}
