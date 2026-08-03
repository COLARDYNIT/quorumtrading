package com.quorumtrading.application.usecase.query;

import com.quorumtrading.model.valueobject.BenchmarkId;

/**
 * Query for the ranked entry list of a single benchmark.
 *
 * @param benchmarkId the benchmark to rank
 */
public record BuildBenchmarkLeaderboardQuery(BenchmarkId benchmarkId) {

    public BuildBenchmarkLeaderboardQuery {
        if (benchmarkId == null) {
            throw new IllegalArgumentException("benchmarkId is required");
        }
    }
}
