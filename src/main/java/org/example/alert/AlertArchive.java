package org.example.alert;

import java.util.List;

/**
 * The end-of-day side of alert storage (Interface Segregation, Dependency
 * Inversion): the daily report archives and reads alerts through this, while
 * chamber code only adds them as an AlertListener. AlertStore implements both.
 */
public interface AlertArchive {

    /** Moves every active alert to the archive and returns the ones moved. */
    List<String> archiveActive();

    int generatedCount();

    int activeCount();

    List<String> archived();
}
