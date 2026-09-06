package com.openbounty.exception;

public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }

    public InvalidStateTransitionException(String entityName, Object currentState, Object targetState) {
        super(String.format("Cannot transition %s from state '%s' to '%s'", entityName, currentState, targetState));
    }
}
