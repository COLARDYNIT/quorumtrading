package com.quorumtrading.adapter.rest.exception;

/**
 * Exception thrown when a requested resource is not found.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(message, 404);
    }

    public NotFoundException(String resourceType, Object id) {
        super(String.format("%s with id '%s' not found", resourceType, id), 404);
    }
}
