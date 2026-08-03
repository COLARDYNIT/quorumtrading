package com.quorumtrading.application.exception;

import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Exception thrown when a user attempts to access a broker account they do not own.
 * This exception results in a 403 Forbidden response.
 */
public class AccountAccessDeniedException extends RuntimeException {

    private final BrokerAccountId accountId;
    private final UserId userId;

    public AccountAccessDeniedException(BrokerAccountId accountId, UserId userId) {
        super(String.format("User %s does not have access to broker account %s",
            userId.value(), accountId.value()));
        this.accountId = accountId;
        this.userId = userId;
    }

    public AccountAccessDeniedException(String message, BrokerAccountId accountId, UserId userId) {
        super(message);
        this.accountId = accountId;
        this.userId = userId;
    }

    public BrokerAccountId getAccountId() {
        return accountId;
    }

    public UserId getUserId() {
        return userId;
    }
}
