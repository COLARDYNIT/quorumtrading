package com.quorumtrading.application.usecase.query;

import com.quorumtrading.model.enums.ExecutionOutcome;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Query for retrieving consensus decisions for a simulation.
 *
 * @param simulationId      simulation unique identifier
 * @param from              optional start timestamp filter
 * @param to                optional end timestamp filter
 * @param executed          optional execution status filter (deprecated, use executionOutcome instead)
 * @param executionOutcome  optional execution outcome filter (PENDING, EXECUTED, NO_TRADE_REQUIRED, REJECTED)
 * @param actions           optional list of consensus actions to filter by (BUY, SELL, HOLD)
 * @param limit             optional maximum number of results
 * @param requestingUserId  user requesting the query (null for system/internal calls)
 */
public record QueryConsensusDecisionsQuery(
    UUID simulationId,
    Instant from,
    Instant to,
    Boolean executed,
    ExecutionOutcome executionOutcome,
    List<String> actions,
    Integer limit,
    UserId requestingUserId
) {
    /** Backward-compatible constructor for internal/system callers. */
    public QueryConsensusDecisionsQuery(UUID simulationId, Instant from, Instant to,
            Boolean executed, ExecutionOutcome executionOutcome, List<String> actions, Integer limit) {
        this(simulationId, from, to, executed, executionOutcome, actions, limit, null);
    }
}
