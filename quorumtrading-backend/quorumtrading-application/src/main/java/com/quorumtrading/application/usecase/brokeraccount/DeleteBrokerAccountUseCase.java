package com.quorumtrading.application.usecase.brokeraccount;

import com.quorumtrading.application.usecase.command.DeleteBrokerAccountCommand;

/**
 * Use case for deleting a broker account.
 *
 * <p><b>Deletion Rules:</b></p>
 * <ul>
 *   <li>Cannot delete account with active simulations (PENDING, SIMULATING, LIVE)</li>
 *   <li>Cannot delete account with reserved capital (reservedBalance > 0)</li>
 *   <li>Account must exist</li>
 * </ul>
 *
 * <p><b>Before Deletion:</b></p>
 * <ul>
 *   <li>Verifies no active simulations are using this broker account</li>
 *   <li>If simulations exist in terminal states (COMPLETED, FAILED), they are not blocking</li>
 * </ul>
 */
public interface DeleteBrokerAccountUseCase {

    /**
     * Execute the delete broker account use case.
     *
     * @param command the command containing the broker account ID to delete
     * @throws com.quorumtrading.application.exception.EntityNotFoundException if broker account not found
     * @throws IllegalStateException if account has active simulations or reserved capital
     */
    void execute(DeleteBrokerAccountCommand command);
}
