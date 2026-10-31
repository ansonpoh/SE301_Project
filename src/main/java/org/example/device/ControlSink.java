package org.example.device;

/**
 * Where chamber threads send device requests (Dependency Inversion): the
 * sensor processor depends on this, not on the queue that implements it.
 */
public interface ControlSink {

    /** May block until the request is accepted. */
    void request(ControlRequest request);
}
