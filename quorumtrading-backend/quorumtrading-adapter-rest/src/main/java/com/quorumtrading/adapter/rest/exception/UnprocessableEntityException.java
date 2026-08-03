package com.quorumtrading.adapter.rest.exception;

/**
 * Exception for 422 Unprocessable Entity errors.
 * Used for semantic errors where the request is well-formed but logically invalid.
 */
public class UnprocessableEntityException extends ApiException {

    public UnprocessableEntityException(String message) {
        super(message, 422);
    }

    public UnprocessableEntityException(String message, Throwable cause) {
        super(message, 422, cause);
    }
}
