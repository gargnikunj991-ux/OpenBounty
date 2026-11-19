package com.openbounty.exception;

/**
 * Thrown when a concurrent request with the same Idempotency-Key is currently in progress.
 * Mapped to HTTP 409 Conflict.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
