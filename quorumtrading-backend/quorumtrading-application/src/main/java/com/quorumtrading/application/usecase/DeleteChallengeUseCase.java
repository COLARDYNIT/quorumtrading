package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.command.DeleteChallengeCommand;

/**
 * Use case for deleting a challenge. Admin-only.
 */
public interface DeleteChallengeUseCase {

    /**
     * Delete a challenge. Detaches all simulations (sets challenge_id = null).
     *
     * @param command the command containing the challenge ID to delete
     * @throws java.util.NoSuchElementException if challenge not found
     */
    void execute(DeleteChallengeCommand command);
}
