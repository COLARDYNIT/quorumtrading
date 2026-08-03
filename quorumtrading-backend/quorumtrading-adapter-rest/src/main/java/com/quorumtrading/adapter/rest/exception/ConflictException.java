package com.quorumtrading.adapter.rest.exception;

/**
 * Exception thrown when an operation conflicts with current state.
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(message, 409);
    }
}
