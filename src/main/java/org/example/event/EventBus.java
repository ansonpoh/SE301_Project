package org.example.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Minimal publish/subscribe channel for one event type (Observer pattern).
 * <p>
 * Publishers do not know who listens, so adding a new reaction to an event (console output,
 * statistics, persistence...) means subscribing a new listener rather than editing a
 * {@code switch} over event names. The listener list is a {@link CopyOnWriteArrayList}, so
 * publishing from many threads while subscribing is safe.
 *
 * @param <E> event type
 */
public final class EventBus<E> {

    private final List<Consumer<? super E>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<? super E> listener) {
        listeners.add(listener);
    }

    /** Delivers the event to every listener; a failing listener does not stop the others. */
    public void publish(E event) {
        for (Consumer<? super E> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                System.err.println("Event listener failed for " + event + ": " + e);
            }
        }
    }
}
