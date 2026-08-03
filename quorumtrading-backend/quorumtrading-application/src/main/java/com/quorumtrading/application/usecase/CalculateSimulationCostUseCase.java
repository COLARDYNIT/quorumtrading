package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.query.CalculateSimulationCostQuery;
import com.quorumtrading.model.valueobject.SimulationCost;

/**
 * Calculates the aggregated AI cost for a simulation by summing
 * token usage and raw cost across all consensus decisions.
 */
public interface CalculateSimulationCostUseCase {
    SimulationCost execute(CalculateSimulationCostQuery query);
}
