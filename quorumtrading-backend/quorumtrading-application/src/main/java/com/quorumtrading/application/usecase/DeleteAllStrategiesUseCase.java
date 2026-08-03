package com.quorumtrading.application.usecase;

import com.quorumtrading.model.valueobject.UserId;

/**
 * Use case for deleting all strategies owned by a specific user.
 * Cascades deletion to all associated simulations and their data.
 */
public interface DeleteAllStrategiesUseCase {

    /**
     * Delete all strategies owned by the given user.
     * Returns the number of strategies deleted.
     *
     * @param userId the owner whose strategies to delete
     * @return number of strategies deleted
     */
    int execute(UserId userId);
}
