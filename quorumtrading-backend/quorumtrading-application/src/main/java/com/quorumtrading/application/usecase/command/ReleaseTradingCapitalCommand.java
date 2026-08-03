package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;

/**
 * Command for releasing trading capital back to a broker account.
 *
 * <p>This command is issued when a simulation ends (COMPLETED or FAILED),
 * to release the previously reserved trading limit back to available balance.</p>
 *
 * @param brokerAccountId the broker account to release capital to
 * @param simulationId    the simulation that is releasing the capital (for tracking/logging)
 * @param amount          the amount to release (the simulation's original trading limit)
 */
public record ReleaseTradingCapitalCommand(
    BrokerAccountId brokerAccountId,
    SimulationId simulationId,
    Money amount
) {
}
