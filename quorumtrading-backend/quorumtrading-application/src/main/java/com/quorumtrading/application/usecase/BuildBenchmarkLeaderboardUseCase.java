package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.dto.BenchmarkLeaderboard;
import com.quorumtrading.application.usecase.query.BuildBenchmarkLeaderboardQuery;

/**
 * Builds the ranked entry list for a benchmark.
 *
 * <p>Single source of truth for benchmark ranking. Driving adapters must not recompute entries
 * from repositories: REST and MCP each did, and disagreed on both the return baseline and the
 * sort order for the same benchmark.</p>
 *
 * <p><strong>Does not authorize.</strong> Callers decide who may see a benchmark before asking
 * for its leaderboard; this use case answers the question for whoever asks.</p>
 */
public interface BuildBenchmarkLeaderboardUseCase {

    /**
     * @throws java.util.NoSuchElementException if the benchmark does not exist
     */
    BenchmarkLeaderboard execute(BuildBenchmarkLeaderboardQuery query);
}
