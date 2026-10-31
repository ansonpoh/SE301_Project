package org.example.device;

import org.example.model.ChamberState;

/**
 * An automated device (Strategy pattern): each implementation changes the
 * chamber state in its own way. Adding a device = one new class + one
 * registration in SVFRMS; ControlService does not change.
 */
public interface Device {

    /** Name used in crops.csv and in log lines, e.g. "Cooling Fan". */
    String name();

    /** Changes the chamber state and returns the log text, e.g. "Temperature reduced to 19.0C". */
    String apply(ChamberState state);
}
