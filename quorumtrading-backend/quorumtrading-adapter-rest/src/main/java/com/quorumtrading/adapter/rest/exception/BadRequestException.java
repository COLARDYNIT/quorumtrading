package com.quorumtrading.adapter.rest.exception;

/**
 * Exception for 400 Bad Request errors.
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(message, 400);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, 400, cause);
    }
}
