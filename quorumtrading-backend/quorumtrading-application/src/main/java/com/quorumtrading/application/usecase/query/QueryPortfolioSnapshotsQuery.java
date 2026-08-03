package com.quorumtrading.application.usecase.query;

import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Query for retrieving portfolio snapshots for a simulation.
 *
 * @param simulationId     simulation unique identifier
 * @param from             optional start timestamp filter
 * @param to               optional end timestamp filter
 * @param limit            optional maximum number of results
 * @param excludePositions when true, omit position details from snapshots (lighter response)
 * @param resolution       snapshot resolution: "all" returns every snapshot, "daily" returns one per day
 * @param requestingUserId The user requesting the data (null for system/internal calls)
 */
public record QueryPortfolioSnapshotsQuery(
    UUID simulationId,
    Instant from,
    Instant to,
    Integer limit,
    boolean excludePositions,
    String resolution,
    UserId requestingUserId
) {
    /**
     * Backward-compatible constructor (4-arg).
     */
    public QueryPortfolioSnapshotsQuery(UUID simulationId, Instant from, Instant to, Integer limit) {
        this(simulationId, from, to, limit, false, "all", null);
    }

    /**
     * Backward-compatible constructor (6-arg) without userId.
     */
    public QueryPortfolioSnapshotsQuery(UUID simulationId, Instant from, Instant to, Integer limit,
                                        boolean excludePositions, String resolution) {
        this(simulationId, from, to, limit, excludePositions, resolution, null);
    }
}
