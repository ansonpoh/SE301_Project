package org.example.device;

import java.io.PrintStream;

/** Prints each device action as the two legacy green "[CH1][CONTROL] ..." lines. */
public class ConsoleActionPrinter implements ActionListener {
    private final PrintStream out;

    public ConsoleActionPrinter(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onAction(String chamberId, String deviceName, String result) {
        log(chamberId, deviceName + " Activated");
        log(chamberId, result);
    }

    private void log(String chamberId, String message) {
        out.println("\t\u001B[32m[" + chamberId + "][CONTROL] " + message + "\u001B[0m");
    }
}
