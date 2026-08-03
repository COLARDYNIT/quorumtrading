package com.quorumtrading.adapter.rest.exception;

import java.util.List;

/**
 * Exception thrown when request validation fails.
 */
public class ValidationException extends ApiException {

    private final List<ProblemDetails.ValidationError> errors;

    public ValidationException(String message, List<ProblemDetails.ValidationError> errors) {
        super(message, 400);
        this.errors = errors;
    }

    public List<ProblemDetails.ValidationError> getErrors() {
        return errors;
    }
}
