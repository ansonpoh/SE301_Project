package org.example.report;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The legacy timestamp format (java.util.Date.toString(), e.g.
 * "Sat Oct 31 17:21:46 GMT+08:00 2026"), in the clock's own time zone. Date.toString()
 * would use the JVM default zone and ignore the injected Clock's zone.
 */
final class Timestamps {
    private static final DateTimeFormatter DAY_AND_TIME = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss", Locale.US);

    private Timestamps() {
    }

    static String now(Clock clock) {
        ZonedDateTime time = ZonedDateTime.now(clock);
        // The zone name exactly the way Date.toString() writes it (e.g. "GMT+08:00"
        // or "PDT"), which DateTimeFormatter's "zzz" does not always match.
        TimeZone zone = TimeZone.getTimeZone(time.getZone());
        boolean daylight = zone.inDaylightTime(Date.from(time.toInstant()));
        return time.format(DAY_AND_TIME) + " " + zone.getDisplayName(daylight, TimeZone.SHORT, Locale.US)
                + " " + time.getYear();
    }
}
