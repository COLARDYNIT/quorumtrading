package com.quorumtrading.application.exception;

/**
 * Exception thrown when a requested entity is not found in the repository.
 * This is an application-level exception indicating the entity does not exist.
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
