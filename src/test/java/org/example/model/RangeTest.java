package org.example.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RangeTest {

    private final Range range = new Range(22, 28);

    @Test
    void boundariesAreInsideTheRange() {
        assertTrue(range.contains(22));
        assertTrue(range.contains(28));
        assertEquals(0, range.deviation(22));
        assertEquals(0, range.deviation(28));
    }

    @Test
    void valuesOutsideReportDirectionAndDeviation() {
        assertTrue(range.isBelow(21.5));
        assertEquals(2, range.deviation(20));
        assertTrue(range.isAbove(31));
        assertEquals(3, range.deviation(31));
        assertFalse(range.contains(31));
    }

    @Test
    void rejectsInvertedRange() {
        assertThrows(IllegalArgumentException.class, () -> new Range(30, 20));
    }
}
