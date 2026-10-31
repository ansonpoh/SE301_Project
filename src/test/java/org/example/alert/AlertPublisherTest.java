package org.example.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class AlertPublisherTest {

    /** A failing listener is reported on its own; the alert still reaches the others and the caller. */
    @Test
    void failingListenerDoesNotStopTheOthers() {
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        AlertPublisher publisher = new AlertPublisher(new PrintStream(errors, true, StandardCharsets.UTF_8));
        AlertStore store = new AlertStore();
        publisher.subscribe(alert -> {
            throw new IllegalStateException("disk full");
        });
        publisher.subscribe(store);

        publisher.onAlert(new Alert("CH1", "Tomato", "CH1 Tomato High Temp", Severity.CRITICAL));

        assertEquals(1, store.generatedCount());
        String text = errors.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Alert listener failed for CH1") && text.contains("disk full"), text);
    }
}
