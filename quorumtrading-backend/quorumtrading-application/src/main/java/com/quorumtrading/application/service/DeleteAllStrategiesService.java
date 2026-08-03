package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.DeleteAllStrategiesUseCase;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;

/**
 * Service implementation for deleting all strategies owned by a specific user.
 * Framework-agnostic, uses constructor injection.
 * Relies on database CASCADE DELETE constraints to handle associated data.
 */
public class DeleteAllStrategiesService implements DeleteAllStrategiesUseCase {

    private final StrategyRepository strategyRepository;

    public DeleteAllStrategiesService(StrategyRepository strategyRepository) {
        this.strategyRepository = strategyRepository;
    }

    @Override
    public int execute(UserId userId) {
        // Find all strategies owned by this user, then delete each one.
        // Database CASCADE DELETE constraint (V18 migration) will automatically
        // delete all associated simulations and their related data:
        // - simulations
        // - llm_decisions
        // - consensus_decisions
        // - portfolio_snapshots
        List<Strategy> strategies = strategyRepository.findByOwnerId(userId);
        for (Strategy strategy : strategies) {
            strategyRepository.deleteById(strategy.getId());
        }
        return strategies.size();
    }
}
