package com.quorumtrading.application.usecase;

import com.quorumtrading.model.entity.ScheduledSimulation;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;

import java.util.Optional;

/**
 * Use case for retrieving a scheduled simulation configuration by strategy ID.
 */
public interface GetScheduledSimulationByStrategyUseCase {

    /**
     * Execute the get scheduled simulation by strategy use case.
     *
     * @param strategyId       the strategy ID to look up
     * @param requestingUserId the user requesting the data (null for system/internal calls)
     * @return Optional containing the scheduled simulation if found, empty otherwise
     */
    Optional<ScheduledSimulation> execute(StrategyId strategyId, UserId requestingUserId);
}
