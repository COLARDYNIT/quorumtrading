package com.quorumtrading.application.usecase;

import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Use case for simulation scheduling and manual triggering.
 *
 * <p><b>DDD Classification:</b> Use Case Interface (Application Layer)</p>
 * Defines the contract for:
 * <ul>
 *   <li>Processing scheduled simulations</li>
 *   <li>Triggering simulations manually outside of scheduled runs</li>
 * </ul>
 */
public interface TriggerSimulationUseCase {

    /**
     * Process all due scheduled simulations.
     * Called periodically by the scheduler adapter.
     */
    void processScheduledSimulations();

    /**
     * Manually triggers a backtest simulation for the given strategy.
     *
     * @param strategyId       The strategy to create a simulation for
     * @param requestingUserId The user requesting the trigger (null for system/internal calls)
     * @return The ID of the created simulation
     * @throws IllegalArgumentException if strategy not found
     */
    SimulationId triggerManually(StrategyId strategyId, UserId requestingUserId);
}
