package com.quorumtrading.application.usecase;

import com.quorumtrading.model.valueobject.UserId;

/**
 * Use case for GDPR-compliant account deletion.
 * Cascade-deletes all user data: strategies, market research, broker accounts,
 * payment transactions, API tokens, notifications, user budget, and the user entity itself.
 */
public interface DeleteUserAccountUseCase {

    /**
     * Delete a user account and all associated data.
     *
     * @param userId the ID of the user requesting account deletion
     * @throws java.util.NoSuchElementException if the user is not found
     */
    void execute(UserId userId);
}
