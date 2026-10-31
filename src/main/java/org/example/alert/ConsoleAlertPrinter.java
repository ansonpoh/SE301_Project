package org.example.alert;

import java.io.PrintStream;

/** Prints each alert as "[CH1][Tomato][ALERT] CH1 Tomato High Temp". */
public class ConsoleAlertPrinter implements AlertListener {
    private final PrintStream out;

    public ConsoleAlertPrinter(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onAlert(Alert alert) {
        out.println("[" + alert.chamberId() + "][" + alert.crop() + "][ALERT] " + alert.message());
    }
}
