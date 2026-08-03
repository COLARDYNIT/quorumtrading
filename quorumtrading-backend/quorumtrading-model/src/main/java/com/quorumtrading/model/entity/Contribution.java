package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.ContributionId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cash injected into a running simulation after it started — the "C" in DCA.
 *
 * <p><b>Append-only child of {@link Simulation}.</b> The Simulation aggregate
 * controls creation via {@link Simulation#buildContribution} (which enforces
 * LIVE state and positive amount). Each contribution is persisted as its own
 * row with a database-level UNIQUE (source_plan_id, contribution_date) so
 * scheduler retries cannot double-fund.</p>
 *
 * <p>Source pointers ({@code sourcePlanId}, {@code sourceAllocationId}) link a
 * contribution back to the recurring investment plan and audit row that
 * produced it. Both are nullable to allow manual / ops-driven contributions
 * outside the plan flow.</p>
 */
public record Contribution(
        ContributionId id,
        SimulationId simulationId,
        Money amount,
        Instant contributionDate,
        UUID sourcePlanId,
        UUID sourceAllocationId,
        Instant createdAt
) {
    public Contribution {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(simulationId, "simulationId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(contributionDate, "contributionDate");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException(
                    "Contribution amount must be positive, got " + amount.format());
        }
    }
}
