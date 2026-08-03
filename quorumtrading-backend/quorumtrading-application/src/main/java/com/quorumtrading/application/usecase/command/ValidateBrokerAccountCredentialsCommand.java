package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Command for re-validating broker account credentials.
 *
 * <p>This command triggers a re-validation of the stored credentials
 * against the broker's API. Used when credentials may have been updated
 * on the broker side or to verify they are still valid.</p>
 *
 * @param brokerAccountId the ID of the broker account to validate
 * @param requestingUserId the user requesting validation. This command carried only the account
 *                         id, so any caller could force a credential probe against any account —
 *                         reaching the live broker API with the victim's stored credentials and
 *                         mutating the account's status.
 */
public record ValidateBrokerAccountCredentialsCommand(
    BrokerAccountId brokerAccountId,
    UserId requestingUserId
) {
}
