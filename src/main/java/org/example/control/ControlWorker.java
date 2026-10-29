package org.example.control;

import java.util.List;
import java.util.Optional;

import org.example.device.Device;
import org.example.event.EventBus;
import org.example.metrics.ResourceMeter;

/**
 * Consumes control requests and operates the requested devices.
 * <p>
 * A failure on one request is reported and skipped instead of killing the thread; in the legacy
 * design a dead consumer left every producer blocked forever on the full queue.
 */
public final class ControlWorker implements Runnable {

    private final ControlQueue queue;
    private final ResourceMeter meter;
    private final EventBus<Activation> activations;

    public ControlWorker(ControlQueue queue, ResourceMeter meter, EventBus<Activation> activations) {
        this.queue = queue;
        this.meter = meter;
        this.activations = activations;
    }

    @Override
    public void run() {
        try {
            Optional<ControlRequest> next;
            while ((next = queue.take()).isPresent()) {
                handleSafely(next.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleSafely(ControlRequest request) {
        try {
            handle(request);
        } catch (RuntimeException e) {
            System.err.println("Could not operate " + request.device().name()
                    + " in " + request.chamber().id() + ": " + e);
        }
    }

    private void handle(ControlRequest request) {
        Device device = request.device();
        String chamberId = request.chamber().id();
        List<String> changes = device.operate(request.chamber().state());
        device.usagePerActivation().forEach(usage -> meter.record(chamberId, usage));
        activations.publish(new Activation(chamberId, device.name(), changes));
    }
}
