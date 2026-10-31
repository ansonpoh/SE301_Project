package org.example.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.example.TestFixtures;
import org.example.model.ChamberState;
import org.example.model.ChamberSummary;
import org.example.model.SensorType;
import org.example.report.ChamberSummaryTracker;
import org.junit.jupiter.api.Test;

class ControlServiceTest {
    private static final PrintStream SILENT = new PrintStream(new ByteArrayOutputStream());

    /** A device that always throws, standing in for any bug inside a device. */
    private static final Device BROKEN = new Device() {
        public String name() {
            return "Broken";
        }

        public String apply(ChamberState state) {
            throw new IllegalStateException("device fault");
        }
    };

    /**
     * Producer-consumer under pressure: 8 producers, a queue of 1. stop() is
     * queued behind every request, so every request is executed before the
     * consumer exits (the legacy shutdown semantics), and nothing deadlocks.
     */
    @Test
    void everyRequestIsExecutedBeforeStop() {
        ChamberSummaryTracker summaries = new ChamberSummaryTracker();
        ControlService control = new ControlService(1, List.of(summaries), SILENT);
        ChamberState state = new ChamberState();
        int producers = 8;
        int requestsEach = 500;

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            ExecutorService consumer = Executors.newSingleThreadExecutor();
            consumer.submit(control);
            TestFixtures.runConcurrently(producers, () -> {
                for (int i = 0; i < requestsEach; i++) {
                    control.request(new ControlRequest("CH1", state, new CoolingFan()));
                }
            });
            control.stop(5, TimeUnit.SECONDS);
            consumer.shutdown();
            consumer.awaitTermination(5, TimeUnit.SECONDS);
        });

        ChamberSummary ch1 = summaries.sortedSummaries().get("CH1");
        assertEquals(producers * requestsEach, ch1.mitigationActions());
        assertEquals("Cooling Fan Activated", ch1.lastAction());
    }

    /**
     * Legacy issue C10: if the control thread died, producers blocked forever on
     * the full queue. Here a failing request is reported and the consumer keeps going.
     */
    @Test
    void failingDeviceDoesNotKillTheConsumer() {
        ChamberSummaryTracker summaries = new ChamberSummaryTracker();
        ControlService control = new ControlService(1, List.of(summaries), SILENT);
        ChamberState state = new ChamberState();

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            ExecutorService consumer = Executors.newSingleThreadExecutor();
            consumer.submit(control);
            for (int i = 0; i < 10; i++) {
                control.request(new ControlRequest("CH1", state, BROKEN));
                control.request(new ControlRequest("CH1", state, new WaterPump()));
            }
            control.stop(5, TimeUnit.SECONDS);
            consumer.shutdown();
            consumer.awaitTermination(5, TimeUnit.SECONDS);
        });

        assertEquals(10, summaries.sortedSummaries().get("CH1").mitigationActions());
    }

    /**
     * Legacy issue C10 for errors the consumer cannot survive: once the control
     * thread is dead, producers fail fast instead of blocking forever on the
     * full queue, and stop() does not block either.
     */
    @Test
    void deadConsumerMakesProducersFailInsteadOfHanging() {
        Device crashing = new Device() {
            public String name() {
                return "Crashing";
            }

            public String apply(ChamberState state) {
                throw new AssertionError("simulated crash");
            }
        };
        ControlService control = new ControlService(1, List.of(new ChamberSummaryTracker()), SILENT);
        ChamberState state = new ChamberState();

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            ExecutorService consumer = Executors.newSingleThreadExecutor();
            consumer.submit(control);
            control.request(new ControlRequest("CH1", state, crashing));
            assertThrows(IllegalStateException.class, () -> {
                while (true) {
                    control.request(new ControlRequest("CH1", state, new CoolingFan()));
                }
            });
            assertTrue(control.stop(5, TimeUnit.SECONDS));
            consumer.shutdown();
            assertTrue(consumer.awaitTermination(5, TimeUnit.SECONDS));
        });
    }

    /**
     * A producer interrupted while the queue is full must not lose its request
     * silently: request() throws (so the chamber is reported as failed) and the
     * interrupt flag is kept. stop() reports that it could not queue the marker.
     */
    @Test
    void interruptedProducerFailsLoudly() {
        ControlService control = new ControlService(1, List.of(new ChamberSummaryTracker()), SILENT);
        ChamberState state = new ChamberState();
        control.request(new ControlRequest("CH1", state, new CoolingFan()));   // queue is now full, no consumer

        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class,
                    () -> control.request(new ControlRequest("CH1", state, new CoolingFan())));
            assertTrue(Thread.currentThread().isInterrupted(), "interrupt flag must be kept");
            assertFalse(control.stop(5, TimeUnit.SECONDS));
        } finally {
            Thread.interrupted();   // clear the flag for the next test
        }
    }

    /** A hung device with a full queue must not block shutdown forever: stop() gives up after its timeout. */
    @Test
    void stopGivesUpWhenTheQueueStaysFull() {
        ControlService control = new ControlService(1, List.of(new ChamberSummaryTracker()), SILENT);
        control.request(new ControlRequest("CH1", new ChamberState(), new CoolingFan()));   // full, no consumer

        assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> assertFalse(control.stop(300, TimeUnit.MILLISECONDS)));
    }

    /** Requests still queued when the control thread dies are reported, not dropped silently. */
    @Test
    void requestsLeftWhenTheConsumerDiesAreReported() throws Exception {
        Device crashing = new Device() {
            public String name() {
                return "Crashing";
            }

            public String apply(ChamberState state) {
                throw new AssertionError("simulated crash");
            }
        };
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        ControlService control = new ControlService(2, List.of(new ChamberSummaryTracker()),
                new PrintStream(errors, true, StandardCharsets.UTF_8));
        ChamberState state = new ChamberState();
        control.request(new ControlRequest("CH1", state, crashing));
        control.request(new ControlRequest("CH2", state, new WaterPump()));   // queued behind the crash

        ExecutorService consumer = Executors.newSingleThreadExecutor();
        Future<?> task = consumer.submit(control);
        assertThrows(ExecutionException.class, () -> task.get(5, TimeUnit.SECONDS));
        consumer.shutdown();

        String text = errors.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Control request not run for CH2: Water Pump"), text);
    }

    /** A failing listener is reported on its own: the device still ran, and the next listener is still told. */
    @Test
    void failingListenerDoesNotHideTheActionFromTheOthers() {
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        ChamberSummaryTracker summaries = new ChamberSummaryTracker();
        ActionListener failing = (chamber, device, result) -> {
            throw new IllegalStateException("listener fault");
        };
        ControlService control = new ControlService(2, List.of(failing, summaries),
                new PrintStream(errors, true, StandardCharsets.UTF_8));

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            ExecutorService consumer = Executors.newSingleThreadExecutor();
            consumer.submit(control);
            control.request(new ControlRequest("CH1", new ChamberState(), new CoolingFan()));
            control.stop(5, TimeUnit.SECONDS);
            consumer.shutdown();
            consumer.awaitTermination(5, TimeUnit.SECONDS);
        });

        assertEquals(1, summaries.sortedSummaries().get("CH1").mitigationActions());
        String text = errors.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Action listener failed for CH1 (Cooling Fan did run)"), text);
        assertFalse(text.contains("Control request failed"), text);
    }

    /** Listeners hear about each device run in the order given; the console printer keeps the legacy text. */
    @Test
    void listenersAreToldAboutEachActionInOrder() {
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        List<String> order = new ArrayList<>();
        ActionListener first = (chamber, device, result) -> order.add("first " + chamber + " " + device);
        ActionListener second = (chamber, device, result) -> order.add("second " + result);
        ControlService control = new ControlService(2, List.of(first, second,
                new ConsoleActionPrinter(new PrintStream(console, true, StandardCharsets.UTF_8))), SILENT);
        ChamberState state = new ChamberState();
        state.set(SensorType.TEMPERATURE, 31);

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            ExecutorService consumer = Executors.newSingleThreadExecutor();
            consumer.submit(control);
            control.request(new ControlRequest("CH1", state, new CoolingFan()));
            control.stop(5, TimeUnit.SECONDS);
            consumer.shutdown();
            consumer.awaitTermination(5, TimeUnit.SECONDS);
        });

        assertEquals(List.of("first CH1 Cooling Fan", "second Temperature reduced to 26.0C"), order);
        String text = console.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("[CH1][CONTROL] Cooling Fan Activated")
                && text.contains("[CH1][CONTROL] Temperature reduced to 26.0C"), text);
    }
}
