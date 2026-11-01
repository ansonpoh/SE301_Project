package org.example.report;

/**
 * Whether today's run completed, as FarmSimulation passes it to every
 * DayEndOutput. INCOMPLETE if a chamber, the control thread, a device or a
 * listener failed, or the run was interrupted, so today's figures are short.
 * Only an output that keeps figures beyond today (the weekly report) needs to
 * look at it.
 */
public enum DayStatus {
    COMPLETE,
    INCOMPLETE
}
