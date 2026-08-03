package com.quorumtrading.application.usecase.brokeraccount;

import com.quorumtrading.application.usecase.command.ValidateBrokerAccountCredentialsCommand;
import com.quorumtrading.model.entity.BrokerAccount;

/**
 * Use case for re-validating broker account credentials.
 *
 * <p><b>Validation Flow:</b></p>
 * <ol>
 *   <li>Retrieve broker account from repository</li>
 *   <li>For MOCK accounts: always succeeds (no credentials to validate)</li>
 *   <li>For real brokers: call broker API to verify credentials are still valid</li>
 *   <li>Update account status based on validation result</li>
 *   <li>Optionally refresh account balance if validation succeeds</li>
 * </ol>
 *
 * <p><b>Outcomes:</b></p>
 * <ul>
 *   <li><b>Success:</b> Account status set to ACTIVE, balance may be updated</li>
 *   <li><b>Failure:</b> Account status set to INVALID_CREDENTIALS</li>
 * </ul>
 */
public interface ValidateBrokerAccountCredentialsUseCase {

    /**
     * Execute the validate broker account credentials use case.
     *
     * @param command the command containing the broker account ID to validate
     * @return the updated BrokerAccount with current status
     * @throws com.quorumtrading.application.exception.EntityNotFoundException if broker account not found
     * @throws com.quorumtrading.application.exception.BrokerCredentialValidationException if validation fails
     */
    BrokerAccount execute(ValidateBrokerAccountCredentialsCommand command);
}
