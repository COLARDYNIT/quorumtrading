package com.quorumtrading.application.usecase.query;

import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Query for retrieving a single broker account by ID.
 *
 * @param brokerAccountId    the ID of the broker account to retrieve
 * @param requestingUserId   the user requesting the account (for authorization)
 */
public record GetBrokerAccountQuery(
    BrokerAccountId brokerAccountId,
    UserId requestingUserId
) {
    /** Backward-compatible constructor for system-level access. */
    public GetBrokerAccountQuery(BrokerAccountId brokerAccountId) {
        this(brokerAccountId, null);
    }
}
