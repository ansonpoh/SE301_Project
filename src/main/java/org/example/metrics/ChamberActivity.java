package org.example.metrics;

/** Immutable summary of what has happened in one chamber. */
public record ChamberActivity(int alerts, int actions, String lastAlert, String lastAction) {

    public static final ChamberActivity NONE = new ChamberActivity(0, 0, "None", "No action");

    public ChamberActivity withAlert(String description) {
        return new ChamberActivity(alerts + 1, actions, description, lastAction);
    }

    public ChamberActivity withAction(String description) {
        return new ChamberActivity(alerts, actions + 1, lastAlert, description);
    }
}
