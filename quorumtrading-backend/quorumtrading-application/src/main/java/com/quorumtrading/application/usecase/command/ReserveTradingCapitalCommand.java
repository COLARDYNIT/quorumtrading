package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;

/**
 * Command for reserving trading capital from a broker account.
 *
 * <p>This command is issued when a simulation starts, to reserve the trading limit
 * from the broker account's available balance.</p>
 *
 * @param brokerAccountId the broker account to reserve capital from
 * @param simulationId    the simulation that is reserving the capital (for tracking)
 * @param amount          the amount to reserve (the simulation's trading limit)
 */
public record ReserveTradingCapitalCommand(
    BrokerAccountId brokerAccountId,
    SimulationId simulationId,
    Money amount
) {
}
