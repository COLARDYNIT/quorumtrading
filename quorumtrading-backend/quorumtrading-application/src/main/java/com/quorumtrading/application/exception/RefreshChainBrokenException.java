package com.quorumtrading.application.exception;

import com.quorumtrading.model.valueobject.BrokerAccountId;

/**
 * Thrown when a Saxo OAuth2 refresh-token chain can no longer be advanced for a broker account.
 *
 * <p>Saxo uses a <strong>rotating</strong> refresh token: each refresh invalidates the previous
 * token. If a refresh fails because the stored refresh token has been invalidated — e.g. a
 * competing instance already rotated it, the token has expired, or the user revoked the
 * authorization — the chain is irrecoverable from the server side and the account owner must
 * re-authorize interactively.</p>
 *
 * <p>The refresh service is expected to react to this by transitioning the account to a
 * re-auth-required state and notifying the owner (see {@code BROKER_REAUTH_REQUIRED}); it must
 * <strong>not</strong> be confused with a transient network/5xx failure, which is retried.</p>
 */
public class RefreshChainBrokenException extends RuntimeException {

    private final transient BrokerAccountId brokerAccountId;

    /**
     * Creates a new refresh-chain-broken exception.
     *
     * @param brokerAccountId the broker account whose refresh chain is broken (non-null)
     * @param message         a description of why the chain is considered broken
     */
    public RefreshChainBrokenException(BrokerAccountId brokerAccountId, String message) {
        super(String.format("Refresh chain broken for broker account %s: %s", brokerAccountId, message));
        this.brokerAccountId = brokerAccountId;
    }

    /**
     * Creates a new refresh-chain-broken exception with an underlying cause.
     *
     * @param brokerAccountId the broker account whose refresh chain is broken (non-null)
     * @param message         a description of why the chain is considered broken
     * @param cause           the underlying exception (e.g. the failed token-endpoint call)
     */
    public RefreshChainBrokenException(BrokerAccountId brokerAccountId, String message, Throwable cause) {
        super(String.format("Refresh chain broken for broker account %s: %s", brokerAccountId, message), cause);
        this.brokerAccountId = brokerAccountId;
    }

    /**
     * The broker account whose refresh chain is broken and therefore needs re-authorization.
     *
     * @return the broker account id
     */
    public BrokerAccountId getBrokerAccountId() {
        return brokerAccountId;
    }
}
