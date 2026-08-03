package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.CreateChallengeCommand;
import com.quorumtrading.model.entity.Challenge;

/**
 * Use case for creating a challenge. Admin-only.
 */
public interface CreateChallengeUseCase {

    /**
     * Create a new challenge.
     *
     * @param command the command containing challenge details
     * @return the created challenge
     * @throws com.quorumtrading.application.exception.AccessDeniedException if user is not admin
     */
    Challenge execute(CreateChallengeCommand command);
}
