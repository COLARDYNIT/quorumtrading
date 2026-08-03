package com.quorumtrading.application.exception;

import com.quorumtrading.model.valueobject.Money;

/**
 * Exception thrown when a broker account has insufficient funds for the requested operation.
 * This exception results in a 422 Unprocessable Entity response.
 */
public class InsufficientFundsException extends RuntimeException {

    private final Money required;
    private final Money available;

    public InsufficientFundsException(Money required, Money available) {
        super(String.format("Insufficient funds. Required: %s, Available: %s", required, available));
        this.required = required;
        this.available = available;
    }

    public InsufficientFundsException(String message, Money required, Money available) {
        super(message);
        this.required = required;
        this.available = available;
    }

    public Money getRequired() {
        return required;
    }

    public Money getAvailable() {
        return available;
    }
}
