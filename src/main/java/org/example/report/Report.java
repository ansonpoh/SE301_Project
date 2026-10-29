package org.example.report;

import java.util.List;

/**
 * One report type (spec section 3.5). New reports are added by implementing this interface and
 * registering them in {@link ReportGenerator}; existing reports are untouched (OCP).
 */
public interface Report {

    String title();

    List<String> lines(FarmSnapshot snapshot);
}
