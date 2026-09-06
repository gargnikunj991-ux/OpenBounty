package com.openbounty.exception;

public class BountyExpiredException extends RuntimeException {

    public BountyExpiredException(String message) {
        super(message);
    }
}
