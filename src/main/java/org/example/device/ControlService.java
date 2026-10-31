package org.example.device;

import java.io.PrintStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.example.report.ChamberSummaryTracker;

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
 */
public class ControlService implements Runnable {
    private static final ControlRequest STOP = new ControlRequest("STOP", null, null);

    private final BlockingQueue<ControlRequest> queue;
    private final ChamberSummaryTracker summaries;
    private final PrintStream out;

    public ControlService(int capacity, ChamberSummaryTracker summaries, PrintStream out) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.summaries = summaries;
        this.out = out;
    }

    /** Called by chamber threads. */
    public void request(ControlRequest request) {
        try {
            queue.put(request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Called once all chamber threads have finished. */
    public void stop() {
        request(STOP);
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
                    System.err.println("Control request failed for " + request.chamberId() + ": " + e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void activate(ControlRequest request) {
        String result = request.execute();
        String activated = request.device().name() + " Activated";
        summaries.recordAction(request.chamberId(), activated);
        log(request.chamberId(), activated);
        log(request.chamberId(), result);
    }

    private void log(String chamberId, String message) {
        out.println("\t\u001B[32m[" + chamberId + "][CONTROL] " + message + "\u001B[0m");
    }
}
