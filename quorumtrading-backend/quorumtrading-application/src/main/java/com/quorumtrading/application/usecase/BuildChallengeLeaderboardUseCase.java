package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.dto.ChallengeLeaderboard;
import com.quorumtrading.application.usecase.query.BuildChallengeLeaderboardQuery;

/**
 * Builds the ranked entry list for a challenge.
 *
 * <p>Single source of truth for challenge ranking. Driving adapters must not recompute
 * entries from repositories: REST and MCP each did, and produced different return
 * percentages for the same challenge.</p>
 */
public interface BuildChallengeLeaderboardUseCase {

    /**
     * @throws java.util.NoSuchElementException if the challenge does not exist
     */
    ChallengeLeaderboard execute(BuildChallengeLeaderboardQuery query);
}
