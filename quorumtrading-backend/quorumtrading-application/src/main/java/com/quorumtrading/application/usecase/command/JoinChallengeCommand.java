package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.ChallengeId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Command for joining a challenge with a strategy.
 * A new simulation is created automatically with the challenge's constraints.
 *
 * @param challengeId the challenge to join
 * @param strategyId the strategy to use for the challenge entry
 * @param requestingUserId the user joining the challenge
 */
public record JoinChallengeCommand(
    ChallengeId challengeId,
    StrategyId strategyId,
    UserId requestingUserId
) {
}
