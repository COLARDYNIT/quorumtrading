package com.quorumtrading.model.exception;

import java.util.List;

/**
 * Exception thrown when strategy content fails validation.
 * Contains a list of specific validation errors for user feedback.
 *
 * <p>This is a domain exception - thrown during strategy creation when
 * required fields are missing or have invalid values. The error list
 * provides actionable feedback for fixing the strategy content.</p>
 */
public class StrategyValidationException extends RuntimeException {

    private final List<String> errors;

    public StrategyValidationException(List<String> errors) {
        super("Strategy validation failed: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public StrategyValidationException(String singleError) {
        this(List.of(singleError));
    }

    /**
     * Returns the list of validation errors.
     * Each error describes a specific issue with the strategy content.
     *
     * @return immutable list of error messages
     */
    public List<String> getErrors() {
        return errors;
    }
}
