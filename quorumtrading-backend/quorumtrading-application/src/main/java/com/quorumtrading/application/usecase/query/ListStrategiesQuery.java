package com.quorumtrading.application.usecase.query;

import com.quorumtrading.model.valueobject.UserId;

import java.util.Optional;

/**
 * Query for listing strategies with optional filtering.
 *
 * @param name               optional filter by strategy name
 * @param limit              optional limit on number of results
 * @param requestingUserId   the user requesting strategies (for authorization scoping)
 */
public record ListStrategiesQuery(
    Optional<String> name,
    Optional<Integer> limit,
    UserId requestingUserId
) {
    /** Backward-compatible constructor without authorization. */
    public ListStrategiesQuery(Optional<String> name, Optional<Integer> limit) {
        this(name, limit, null);
    }

    /**
     * Create a query with no filters.
     */
    public static ListStrategiesQuery all() {
        return new ListStrategiesQuery(
            Optional.empty(),
            Optional.empty(),
            null
        );
    }

    /**
     * Create a query scoped to a user.
     */
    public static ListStrategiesQuery forUser(UserId userId) {
        return new ListStrategiesQuery(
            Optional.empty(),
            Optional.empty(),
            userId
        );
    }
}
