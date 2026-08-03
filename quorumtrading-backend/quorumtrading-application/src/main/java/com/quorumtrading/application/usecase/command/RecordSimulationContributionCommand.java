package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Inject cash into a LIVE simulation.
 *
 * @param simulationId        target simulation (must be LIVE)
 * @param amount              positive cash amount
 * @param contributionDate    as-of date, used for TWR sub-period chaining and
 *                            as the idempotency key paired with {@code sourcePlanId}
 * @param sourcePlanId        optional: the recurring plan that triggered this contribution
 * @param sourceAllocationId  optional: the allocation audit row that this contribution belongs to
 * @param requestingUserId    the user injecting the cash. This command carried no user at all,
 *                            so any caller could top up any LIVE simulation's portfolio and the
 *                            parent broker account's reserved balance.
 */
public record RecordSimulationContributionCommand(
        SimulationId simulationId,
        Money amount,
        Instant contributionDate,
        UUID sourcePlanId,
        UUID sourceAllocationId,
        UserId requestingUserId
) {
    public RecordSimulationContributionCommand {
        if (simulationId == null) throw new IllegalArgumentException("simulationId is required");
        if (amount == null) throw new IllegalArgumentException("amount is required");
        if (!amount.isPositive()) throw new IllegalArgumentException("amount must be positive");
        if (contributionDate == null) throw new IllegalArgumentException("contributionDate is required");
    }
}
