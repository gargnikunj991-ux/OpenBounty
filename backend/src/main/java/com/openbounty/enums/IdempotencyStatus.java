package com.openbounty.enums;

/**
 * Execution lifecycle states of an idempotent HTTP request.
 */
public enum IdempotencyStatus {
    /**
     * Request is currently executing inside the backend service pipeline.
     */
    PROCESSING,

    /**
     * Request completed successfully and response payload is cached for replay.
     */
    COMPLETED,

    /**
     * Request resulted in a failure or exception; subsequent retries may execute anew.
     */
    FAILED
}
