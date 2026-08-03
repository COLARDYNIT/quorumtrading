package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.BrokerCredentials;
import com.quorumtrading.model.valueobject.UserId;

import java.util.Objects;

/**
 * Command to update the credentials (and optionally name) of an existing broker account.
 *
 * @param brokerAccountId the broker account to update
 * @param requestingUserId the user requesting the update (for ownership verification)
 * @param name optional new display name (null keeps current)
 * @param credentials the new API credentials
 */
public record UpdateBrokerAccountCredentialsCommand(
    BrokerAccountId brokerAccountId,
    UserId requestingUserId,
    String name,
    BrokerCredentials credentials
) {
    public UpdateBrokerAccountCredentialsCommand {
        Objects.requireNonNull(brokerAccountId, "brokerAccountId cannot be null");
        Objects.requireNonNull(requestingUserId, "requestingUserId cannot be null");
        Objects.requireNonNull(credentials, "credentials cannot be null");
    }
}
