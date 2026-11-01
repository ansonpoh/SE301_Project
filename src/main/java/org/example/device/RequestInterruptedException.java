package org.example.device;

/**
 * The calling thread was interrupted while waiting to queue a device request
 * (the interrupt flag is kept). The run is being stopped, so its chamber ends
 * as a partial day, not a failure. An IllegalStateException, so callers that
 * catch any failed request still catch this one.
 */
public class RequestInterruptedException extends IllegalStateException {

    public RequestInterruptedException(String message, InterruptedException cause) {
        super(message, cause);
    }
}
