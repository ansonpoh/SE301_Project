package org.example.control;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Bounded hand-off between chamber monitors (producers) and the control worker (consumer).
 * <p>
 * Replaces the legacy hand-written {@code wait()/notifyAll()} on an {@code ArrayList}, which
 * swallowed {@code InterruptedException} and needed a separate volatile flag to stop.
 * {@link ArrayBlockingQueue} provides the blocking and signalling; shutdown is an end-of-stream
 * marker placed behind any pending requests, so none are lost.
 */
public final class ControlQueue {

    private static final Optional<ControlRequest> END_OF_STREAM = Optional.empty();

    private final BlockingQueue<Optional<ControlRequest>> queue;

    public ControlQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /** Blocks while the queue is full. */
    public void submit(ControlRequest request) throws InterruptedException {
        queue.put(Optional.of(request));
    }

    /** Signals the consumer to stop once it has drained the requests already queued. */
    public void close() throws InterruptedException {
        queue.put(END_OF_STREAM);
    }

    /**
     * Blocks until a request is available.
     *
     * @return the next request, or empty once the queue has been closed
     */
    public Optional<ControlRequest> take() throws InterruptedException {
        return queue.take();
    }
}
