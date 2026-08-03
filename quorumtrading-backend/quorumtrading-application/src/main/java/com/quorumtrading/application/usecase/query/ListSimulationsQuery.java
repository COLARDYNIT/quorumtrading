package com.quorumtrading.application.usecase.query;

import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;
import java.util.Optional;

/**
 * Query for listing simulations with optional filtering.
 *
 * @param status             optional filter by simulation status (e.g., "PENDING", "SIMULATING", "LIVE", "COMPLETED")
 * @param strategyId         optional filter by strategy ID
 * @param since              optional filter by startedAt >= since (only simulations started after or at this time)
 * @param limit              optional limit on number of results
 * @param requestingUserId   the user requesting simulations (for authorization scoping)
 */
public record ListSimulationsQuery(
    Optional<String> status,
    Optional<StrategyId> strategyId,
    Optional<Instant> since,
    Optional<Integer> limit,
    UserId requestingUserId
) {
    /** Backward-compatible constructor without authorization. */
    public ListSimulationsQuery(
        Optional<String> status,
        Optional<StrategyId> strategyId,
        Optional<Instant> since,
        Optional<Integer> limit
    ) {
        this(status, strategyId, since, limit, null);
    }

    /**
     * Create a query with no filters.
     */
    public static ListSimulationsQuery all() {
        return new ListSimulationsQuery(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            null
        );
    }

    /**
     * Create a query scoped to a user.
     */
    public static ListSimulationsQuery forUser(UserId userId) {
        return new ListSimulationsQuery(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            userId
        );
    }
}
