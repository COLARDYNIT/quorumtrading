package com.quorumtrading.application.usecase.brokeraccount;

import com.quorumtrading.application.usecase.command.UpdateBrokerAccountCredentialsCommand;
import com.quorumtrading.model.entity.BrokerAccount;

/**
 * Use case for updating broker account credentials (and optionally name).
 *
 * <p>Allows users to rotate API keys without deleting and recreating the account,
 * which would be blocked if active simulations are using the account.</p>
 *
 * <p><b>Flow:</b></p>
 * <ol>
 *   <li>Load account and verify ownership</li>
 *   <li>Update name if provided</li>
 *   <li>Update credentials</li>
 *   <li>Validate new credentials against the broker API</li>
 *   <li>On success: mark active, sync balance/equity, recalculate reserved balance</li>
 *   <li>On failure: mark INVALID_CREDENTIALS</li>
 * </ol>
 */
public interface UpdateBrokerAccountCredentialsUseCase {

    /**
     * Execute the update broker account credentials use case.
     *
     * @param command the command containing the account ID, new credentials, and optional new name
     * @return the updated BrokerAccount with current status
     * @throws com.quorumtrading.application.exception.EntityNotFoundException if broker account not found
     * @throws com.quorumtrading.application.exception.AccessDeniedException if user doesn't own the account
     * @throws com.quorumtrading.application.exception.BrokerCredentialValidationException if new credentials are invalid
     * @throws IllegalStateException if called on a mock account
     */
    BrokerAccount execute(UpdateBrokerAccountCredentialsCommand command);
}
