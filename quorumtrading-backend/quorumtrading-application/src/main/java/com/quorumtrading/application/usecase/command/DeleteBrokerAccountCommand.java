package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Command for deleting a broker account.
 *
 * @param brokerAccountId the ID of the broker account to delete
 * @param requestingUserId ID of the user requesting deletion (for authorization)
 */
public record DeleteBrokerAccountCommand(
    BrokerAccountId brokerAccountId,
    UserId requestingUserId
) {
}
