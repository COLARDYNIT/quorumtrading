package com.quorumtrading.application.usecase.brokeraccount;

import com.quorumtrading.application.usecase.command.ReleaseTradingCapitalCommand;

/**
 * Use case for releasing trading capital back to a broker account when a simulation ends.
 *
 * <p><b>Called When:</b> Simulation transitions to COMPLETED or FAILED state</p>
 *
 * <p><b>Release Process:</b></p>
 * <ol>
 *   <li>Validate broker account exists</li>
 *   <li>Release the reserved capital (moves from reserved back to available)</li>
 *   <li>Persist the updated broker account</li>
 * </ol>
 *
 * <p><b>Balance Calculation:</b></p>
 * <ul>
 *   <li>After release: Reserved Balance -= Released Amount</li>
 *   <li>Available Balance = Total Balance - Reserved Balance (automatically increases)</li>
 * </ul>
 *
 * <p><b>Note:</b> The amount released should be the original trading limit, not the
 * current portfolio value. P&L tracking is separate from capital reservation.</p>
 *
 * @see com.quorumtrading.model.entity.BrokerAccount#release(com.quorumtrading.model.valueobject.Money)
 */
public interface ReleaseTradingCapitalUseCase {

    /**
     * Execute the release trading capital use case.
     *
     * @param command the command containing broker account ID and amount to release
     * @throws com.quorumtrading.application.exception.EntityNotFoundException if broker account not found
     * @throws IllegalStateException if releasing more than reserved balance
     */
    void execute(ReleaseTradingCapitalCommand command);
}
