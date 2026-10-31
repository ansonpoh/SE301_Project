package org.example.device;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Producer-consumer control queue.
 *
 * Chamber threads call request() (blocks while the queue is full, like the
 * legacy wait() loop). The control thread runs run(), which takes requests in
 * FIFO order and executes them. ArrayBlockingQueue replaces the hand-written
 * wait/notifyAll buffer.
 *
 * stop() queues a STOP marker behind every pending request, so the queue is
 * drained before the control thread exits (same as the legacy shutdown flag).
 *
 * A request is never dropped silently: if a producer is interrupted while the
 * queue is full, request() throws, so its chamber fails and is reported; if the
 * control thread dies, every request still queued is reported as not run.
 *
 * If the control thread dies (an Error inside a device, or it is interrupted),
 * request() throws instead of waiting forever on a queue nobody empties
 * (legacy issue C10).
 *
 * It only queues and runs requests. What happens after a device runs (the
 * report summary, the console lines) is up to its ActionListeners (Observer),
 * so this class depends on nothing outside the device package.
 */
public class ControlService implements ControlSink, Runnable {
    private static final ControlRequest STOP = new ControlRequest("STOP", null, null);
    /** How often a blocked producer checks that the control thread is still alive. */
    private static final long LIVENESS_CHECK_MILLIS = 100;

    private final BlockingQueue<ControlRequest> queue;
    private final List<ActionListener> listeners;
    private final PrintStream err;
    /** Set when run() exits other than through STOP. */
    private volatile boolean consumerDead = false;

    /** @param listeners told about every device run, in this order */
    public ControlService(int capacity, List<ActionListener> listeners, PrintStream err) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.listeners = List.copyOf(listeners);
        this.err = err;
    }

    /**
     * Called by chamber threads. Blocks while the queue is full.
     *
     * @throws IllegalStateException if interrupted while waiting (the interrupt flag is kept),
     *                               or if the control thread has died
     */
    @Override
    public void request(ControlRequest request) {
        try {
            while (!consumerDead) {
                if (queue.offer(request, LIVENESS_CHECK_MILLIS, TimeUnit.MILLISECONDS)) {
                    // The consumer may have died just as the request went in. If the
                    // request is still queued nobody will take it, so take it back and
                    // fail like any other dead-consumer call. If it is gone, the
                    // consumer either ran it or die() reported it as not run, so it is
                    // accounted for exactly once and this call succeeded.
                    if (!consumerDead || !queue.remove(request)) {
                        return;
                    }
                    break;
                }
            }
            throw new IllegalStateException("Control thread has stopped; cannot queue "
                    + request.device().name() + " for " + request.chamberId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while queueing " + request.device().name()
                    + " for " + request.chamberId(), e);
        }
    }

    /**
     * Called once all chamber threads have finished. Waits at most the given
     * time for room in the queue, so a hung device cannot block shutdown forever.
     *
     * @return false if the stop marker could not be queued in time or the wait
     *         was interrupted; the caller must then stop the consumer itself
     */
    public boolean stop(long timeout, TimeUnit unit) {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        try {
            while (!queue.offer(STOP, LIVENESS_CHECK_MILLIS, TimeUnit.MILLISECONDS)) {
                if (consumerDead) {
                    return true;   // nothing left to stop
                }
                if (System.nanoTime() - deadline >= 0) {
                    return false;
                }
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                ControlRequest request = queue.take();
                if (request == STOP) {
                    return;
                }
                // One bad request must not kill the consumer: producers would
                // then block forever on a full queue (legacy issue C10).
                try {
                    activate(request);
                } catch (RuntimeException e) {
                    err.println("Control request failed for " + request.chamberId() + ": " + e);
                }
            }
        } catch (InterruptedException e) {
            die();
            Thread.currentThread().interrupt();
        } catch (Error e) {
            err.println("Control thread failed: " + e);
            die();
            throw e;
        }
    }

    /**
     * Marks the consumer dead, then reports every request still queued, so none
     * is lost silently. Producers check the flag after queueing (see request()),
     * so a request queued after this drain is taken back by its producer, and
     * one drained here is not reported again by its producer.
     */
    private void die() {
        consumerDead = true;
        List<ControlRequest> lost = new ArrayList<>();
        queue.drainTo(lost);
        lost.remove(STOP);
        for (ControlRequest request : lost) {
            err.println("Control request not run for " + request.chamberId() + ": "
                    + request.device().name() + " (control thread stopped)");
        }
    }

    /**
     * The device runs first; a listener that throws is reported on its own, so
     * it is never mistaken for a failed device and the other listeners still run.
     */
    private void activate(ControlRequest request) {
        String result = request.execute();
        for (ActionListener listener : listeners) {
            try {
                listener.onAction(request.chamberId(), request.device().name(), result);
            } catch (RuntimeException e) {
                err.println("Action listener failed for " + request.chamberId() + " ("
                        + request.device().name() + " did run): " + e);
            }
        }
    }
}
