package com.quorumtrading.application.usecase;

import com.quorumtrading.model.valueobject.SimulationId;

/**
 * Result of evaluating a single simulation's portfolio.
 *
 * @param simulationId the simulation that was evaluated
 * @param success whether evaluation completed successfully
 * @param skipped whether evaluation was skipped
 * @param errorMessage error message if evaluation failed
 */
public record SimulationEvaluationResult(
        SimulationId simulationId,
        boolean success,
        boolean skipped,
        String errorMessage
) {
    public static SimulationEvaluationResult success(SimulationId simulationId) {
        return new SimulationEvaluationResult(simulationId, true, false, null);
    }

    public static SimulationEvaluationResult skipped(SimulationId simulationId, String reason) {
        return new SimulationEvaluationResult(simulationId, false, true, reason);
    }

    public static SimulationEvaluationResult failed(SimulationId simulationId, String error) {
        return new SimulationEvaluationResult(simulationId, false, false, error);
    }
}
