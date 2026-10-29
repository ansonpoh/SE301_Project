package org.example.app;

import java.io.PrintStream;

import org.example.alert.Alert;
import org.example.control.Activation;

/**
 * Prints alerts and device activations to the operator console.
 * Each event is printed with a single call so lines from different threads never interleave.
 */
public final class ConsoleEventPrinter {

    private static final String GREEN = "\u001B[32m";
    private static final String RESET = "\u001B[0m";

    private final PrintStream out;

    public ConsoleEventPrinter(PrintStream out) {
        this.out = out;
    }

    public void printAlert(Alert alert) {
        out.println("[" + alert.chamberId() + "][" + alert.cropName() + "][" + alert.severity() + "] "
                + alert.message());
    }

    public void printActivation(Activation activation) {
        StringBuilder text = new StringBuilder(controlLine(activation.chamberId(), activation.summary()));
        for (String change : activation.changes()) {
            text.append(System.lineSeparator()).append(controlLine(activation.chamberId(), change));
        }
        out.println(text);
    }

    private static String controlLine(String chamberId, String message) {
        return "\t" + GREEN + "[" + chamberId + "][CONTROL] " + message + RESET;
    }
}
