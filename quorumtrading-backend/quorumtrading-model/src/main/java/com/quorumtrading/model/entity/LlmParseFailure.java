package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.SimulationId;

import java.time.Instant;

/**
 * Records a failed LLM response parse attempt for troubleshooting.
 * Captures the raw response, prompt, model info, and error details
 * so that parse failures can be investigated after the fact.
 */
public record LlmParseFailure(
        SimulationId simulationId,
        Instant decisionTimestamp,
        String ticker,
        String agentRole,
        String requestedModel,
        String actualModel,
        Double temperature,
        int executionTimeMs,
        String prompt,
        String rawResponse,
        String errorMessage,
        String errorType
) {

    /**
     * Factory method that captures failure details from an exception.
     * Call this from the catch block where the parse failure occurs.
     */
    public static LlmParseFailure capture(
            SimulationId simulationId,
            Instant decisionTimestamp,
            String ticker,
            String agentRole,
            String actualModel,
            int executionTimeMs,
            String prompt,
            String rawResponse,
            Exception error) {
        return new LlmParseFailure(
                simulationId,
                decisionTimestamp,
                ticker,
                agentRole,
                null,
                actualModel,
                null,
                executionTimeMs,
                prompt,
                rawResponse,
                error.getMessage() != null ? error.getMessage() : error.getClass().getName() + " (no message)",
                error.getClass().getName()
        );
    }

    /**
     * Returns a copy with the requested model set (known by the agent, not the parser).
     */
    public LlmParseFailure withRequestedModel(String requestedModel) {
        return new LlmParseFailure(
                simulationId, decisionTimestamp, ticker, agentRole,
                requestedModel, actualModel, temperature, executionTimeMs,
                prompt, rawResponse, errorMessage, errorType
        );
    }

    /**
     * Returns a copy with the temperature set (known by the agent, not the parser).
     */
    public LlmParseFailure withTemperature(double temperature) {
        return new LlmParseFailure(
                simulationId, decisionTimestamp, ticker, agentRole,
                requestedModel, actualModel, temperature, executionTimeMs,
                prompt, rawResponse, errorMessage, errorType
        );
    }
}
