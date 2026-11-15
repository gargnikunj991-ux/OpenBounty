package com.openbounty.exception;

/**
 * Thrown when an Idempotency-Key is reused with a different request payload or target endpoint.
 * Mapped to HTTP 422 Unprocessable Entity.
 */
public class IdempotencyPayloadMismatchException extends RuntimeException {
    public IdempotencyPayloadMismatchException(String message) {
        super(message);
    }
}
