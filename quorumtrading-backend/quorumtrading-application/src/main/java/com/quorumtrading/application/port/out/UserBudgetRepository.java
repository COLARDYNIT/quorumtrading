package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.UserBudgetId;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for UserBudget aggregate persistence.
 */
public interface UserBudgetRepository {

    /**
     * Find a user's budget by user ID.
     */
    Optional<UserBudget> findByUserId(UserId userId);

    /**
     * Find a budget by its ID.
     */
    Optional<UserBudget> findById(UserBudgetId id);

    /**
     * Save a user budget (create or update).
     */
    UserBudget save(UserBudget userBudget);

    /**
     * Delete a user budget.
     */
    void delete(UserBudgetId id);

    /**
     * Check if a budget exists for a user.
     */
    boolean existsByUserId(UserId userId);

    /**
     * Find all user budgets (for admin aggregation).
     */
    List<UserBudget> findAll();
}
