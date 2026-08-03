package com.quorumtrading.application.usecase.query;

import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Query to find a stock/crypto ticker by company name.
 *
 * @param companyName     the company name to search for (e.g., "Apple", "Tesla")
 * @param userId          the user requesting the search (for budget tracking)
 * @param brokerAccountId optional broker account to validate tradability against; {@code null}
 *                        means validate against the global-default broker (today's behavior).
 *                        Account-aware validation itself is deferred (FT2) — for now this field is
 *                        threaded through but does not change validation behavior.
 */
public record FindTickerQuery(String companyName, UserId userId, BrokerAccountId brokerAccountId) {

    public FindTickerQuery {
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("Company name cannot be null or blank");
        }
        if (userId == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
    }

    /**
     * Convenience constructor for the global-default broker (no specific account).
     */
    public FindTickerQuery(String companyName, UserId userId) {
        this(companyName, userId, null);
    }
}
