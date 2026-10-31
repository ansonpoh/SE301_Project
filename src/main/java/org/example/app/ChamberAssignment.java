package org.example.app;

/** One row of chambers.csv: which crop grows in which chamber. */
public record ChamberAssignment(String chamberId, String crop) {
}
