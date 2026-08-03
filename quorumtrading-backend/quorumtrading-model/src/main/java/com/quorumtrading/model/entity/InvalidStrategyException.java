package com.quorumtrading.model.entity;

/**
 * Exception thrown when a strategy fails domain validation.
 * This is a domain exception representing business rule violations.
 */
public class InvalidStrategyException extends RuntimeException {

    public InvalidStrategyException(String message) {
        super(message);
    }

    public InvalidStrategyException(String message, Throwable cause) {
        super(message, cause);
    }
}
