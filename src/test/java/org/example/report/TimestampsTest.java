package org.example.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

import org.junit.jupiter.api.Test;

class TimestampsTest {
    /** Same format as the legacy Date.toString(), but in the injected clock's zone, not the JVM's. */
    @Test
    void usesTheLegacyFormatInTheClocksZone() {
        Instant instant = Instant.parse("2026-10-31T09:21:46Z");

        assertEquals("Sat Oct 31 09:21:46 UTC 2026", Timestamps.now(Clock.fixed(instant, ZoneId.of("UTC"))));
        // The zone's short name depends on the JDK's locale data ("SGT" or "GMT+08:00"), the time does not.
        String singapore = Timestamps.now(Clock.fixed(instant, ZoneId.of("Asia/Singapore")));
        assertTrue(singapore.startsWith("Sat Oct 31 17:21:46 ") && singapore.endsWith(" 2026"), singapore);
        String losAngeles = Timestamps.now(Clock.fixed(instant, ZoneId.of("America/Los_Angeles")));
        assertTrue(losAngeles.startsWith("Sat Oct 31 02:21:46 ") && losAngeles.endsWith(" 2026"), losAngeles);
    }

    /** In the JVM's own zone the text is exactly what the legacy new Date().toString() printed. */
    @Test
    void matchesLegacyDateToStringInTheDefaultZone() {
        Instant instant = Instant.parse("2026-01-15T03:04:05Z");

        assertEquals(Date.from(instant).toString(), Timestamps.now(Clock.fixed(instant, ZoneId.systemDefault())));
    }
}
