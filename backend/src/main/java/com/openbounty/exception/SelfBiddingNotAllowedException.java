package com.openbounty.exception;

public class SelfBiddingNotAllowedException extends RuntimeException {

    public SelfBiddingNotAllowedException(String message) {
        super(message);
    }
}
