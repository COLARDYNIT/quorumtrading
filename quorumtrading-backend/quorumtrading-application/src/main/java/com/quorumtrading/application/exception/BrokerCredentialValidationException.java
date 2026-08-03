package com.quorumtrading.application.exception;

import com.quorumtrading.model.enums.BrokerType;

/**
 * Exception thrown when broker credential validation fails.
 *
 * <p>This exception is thrown during broker account creation when the provided
 * API credentials cannot be validated against the broker's API.</p>
 *
 * <p><b>Common Causes:</b></p>
 * <ul>
 *   <li>Invalid API key or secret</li>
 *   <li>Expired credentials</li>
 *   <li>Account suspended or disabled</li>
 *   <li>Broker API temporarily unavailable</li>
 *   <li>Wrong broker type for the credentials (e.g., paper credentials with ALPACA_LIVE)</li>
 * </ul>
 */
public class BrokerCredentialValidationException extends RuntimeException {

    private final BrokerType brokerType;

    /**
     * Creates a new broker credential validation exception.
     *
     * @param brokerType the type of broker where validation failed
     * @param message    description of why validation failed
     */
    public BrokerCredentialValidationException(BrokerType brokerType, String message) {
        super(String.format("Credential validation failed for %s: %s", brokerType, message));
        this.brokerType = brokerType;
    }

    /**
     * Creates a new broker credential validation exception with a cause.
     *
     * @param brokerType the type of broker where validation failed
     * @param message    description of why validation failed
     * @param cause      the underlying exception
     */
    public BrokerCredentialValidationException(BrokerType brokerType, String message, Throwable cause) {
        super(String.format("Credential validation failed for %s: %s", brokerType, message), cause);
        this.brokerType = brokerType;
    }

    /**
     * Gets the broker type where validation failed.
     *
     * @return the broker type
     */
    public BrokerType getBrokerType() {
        return brokerType;
    }
}
