package com.quorumtrading.application.usecase.query;

import com.quorumtrading.model.valueobject.ChallengeId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Query for the ranked entry list of a single challenge.
 *
 * @param challengeId the challenge to rank
 * @param viewerId    who is looking, or {@code null} for an anonymous viewer. Only affects
 *                    which in-progress entries are visible (you may see your own SIMULATING
 *                    entry, never someone else's) and which entries are flagged
 *                    {@code ownedByViewer}. It never changes the computed numbers or ranks
 *                    of the entries that are visible to everyone.
 */
public record BuildChallengeLeaderboardQuery(ChallengeId challengeId, UserId viewerId) {

    public BuildChallengeLeaderboardQuery {
        if (challengeId == null) {
            throw new IllegalArgumentException("challengeId is required");
        }
    }

    /** Leaderboard as seen by someone who is not signed in. */
    public static BuildChallengeLeaderboardQuery anonymous(ChallengeId challengeId) {
        return new BuildChallengeLeaderboardQuery(challengeId, null);
    }
}
