package com.quorumtrading.application.usecase.brokeraccount;

import com.quorumtrading.application.usecase.command.ReserveTradingCapitalCommand;

/**
 * Use case for reserving trading capital from a broker account when a simulation starts.
 *
 * <p><b>Called When:</b> Simulation transitions from PENDING to SIMULATING/LIVE</p>
 *
 * <p><b>Reservation Process:</b></p>
 * <ol>
 *   <li>Validate broker account exists and is ACTIVE</li>
 *   <li>Validate simulation's trading limit does not exceed available balance</li>
 *   <li>Reserve the capital on the broker account (moves from available to reserved)</li>
 *   <li>Persist the updated broker account</li>
 * </ol>
 *
 * <p><b>Balance Calculation:</b></p>
 * <ul>
 *   <li>Available Balance = Total Balance - Reserved Balance</li>
 *   <li>After reservation: Reserved Balance += Trading Limit</li>
 * </ul>
 *
 * @see com.quorumtrading.model.entity.BrokerAccount#reserve(com.quorumtrading.model.valueobject.Money)
 */
public interface ReserveTradingCapitalUseCase {

    /**
     * Execute the reserve trading capital use case.
     *
     * @param command the command containing broker account ID and amount to reserve
     * @throws com.quorumtrading.application.exception.EntityNotFoundException if broker account not found
     * @throws IllegalStateException if broker account is not ACTIVE or insufficient available balance
     */
    void execute(ReserveTradingCapitalCommand command);
}
