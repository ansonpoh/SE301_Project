package org.example.alert;

/** Observer interface: anything that reacts to a new alert. */
public interface AlertListener {
    void onAlert(Alert alert);
}
