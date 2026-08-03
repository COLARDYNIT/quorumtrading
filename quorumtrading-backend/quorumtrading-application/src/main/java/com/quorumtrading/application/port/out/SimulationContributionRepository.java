package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.valueobject.ContributionId;
import com.quorumtrading.model.valueobject.SimulationId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port for {@link Contribution} persistence.
 *
 * <p>Append-only stream — no update or delete except cascading from the parent
 * simulation. The DB enforces UNIQUE (source_plan_id, contribution_date) so
 * retried plan executions can probe for an existing row and idempotently skip.</p>
 */
public interface SimulationContributionRepository {

    Contribution save(Contribution contribution);

    Optional<Contribution> findById(ContributionId id);

    /**
     * All contributions for a simulation, ordered by contribution date (ascending).
     */
    List<Contribution> findBySimulationId(SimulationId simulationId);

    /**
     * Batch variant of {@link #findBySimulationId}: all contributions for the given
     * simulations, grouped by simulation id (each group ordered by contribution date).
     * Simulations with no contributions are absent from the map. Used to avoid an N+1
     * when enriching a page of simulations with contribution-aware returns.
     */
    Map<SimulationId, List<Contribution>> findBySimulationIds(Collection<SimulationId> simulationIds);

    /**
     * Look up an existing contribution by its idempotency key. Returns the
     * existing row when the plan has already funded this simulation on that
     * exact date — caller should treat presence as "already done, skip".
     */
    Optional<Contribution> findByPlanAndDate(UUID sourcePlanId, Instant contributionDate);
}
