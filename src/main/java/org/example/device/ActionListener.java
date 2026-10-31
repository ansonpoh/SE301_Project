package org.example.device;

/**
 * Observer interface for device actions, the counterpart of AlertListener:
 * anything that reacts to a device having run (the report summary, the console).
 * Called on the control thread.
 */
public interface ActionListener {

    /** @param result the device's log text, e.g. "Temperature reduced to 19.0C" */
    void onAction(String chamberId, String deviceName, String result);
}
