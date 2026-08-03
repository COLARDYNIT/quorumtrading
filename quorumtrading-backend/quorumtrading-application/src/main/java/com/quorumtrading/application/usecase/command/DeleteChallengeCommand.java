package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.ChallengeId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Command for deleting a challenge.
 *
 * @param challengeId ID of the challenge to delete
 * @param requestingUserId ID of the user requesting deletion (must be admin)
 */
public record DeleteChallengeCommand(
    ChallengeId challengeId,
    UserId requestingUserId
) {
}
