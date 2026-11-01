package org.example.device;

import java.util.Set;

import org.example.model.ChamberState;

/**
 * An automated device (Strategy pattern): each implementation changes the
 * chamber state in its own way. Adding a device = one new class + one line
 * in META-INF/services/org.example.device.Device (found by DeviceRegistry.discover());
 * no existing class changes. An implementation needs a public no-argument constructor.
 */
public interface Device {

    /** Name used in crops.csv and in log lines, e.g. "Cooling Fan". */
    String name();

    /** Changes the chamber state and returns the log text, e.g. "Temperature reduced to 19.0C". */
    String apply(ChamberState state);

    /**
     * The readings apply() moves and which way, e.g. {lowers TEMPERATURE}.
     * FarmConfigLoader rejects a crops.csv row whose device would not correct
     * its breach (e.g. a Cooling Fan on a LOW temperature rule).
     */
    Set<Effect> effects();
}
