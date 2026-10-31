package org.example.device;

/**
 * Where chamber threads send device requests (Dependency Inversion): the
 * sensor processor depends on this, not on the queue that implements it.
 */
public interface ControlSink {

    /**
     * May block until the request is accepted.
     *
     * @throws IllegalStateException if the request cannot be queued; its cause is an
     *         InterruptedException when the calling thread was interrupted while
     *         waiting (the interrupt flag is kept), so the caller can stop cleanly
     */
    void request(ControlRequest request);
}
