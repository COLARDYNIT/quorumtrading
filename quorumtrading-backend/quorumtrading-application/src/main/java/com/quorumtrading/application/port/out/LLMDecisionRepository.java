package com.quorumtrading.application.port.out;

import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.valueobject.SimulationId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for LLMDecision entities.
 * Implemented by persistence adapters to provide data access.
 */
public interface LLMDecisionRepository {
    LLMDecision save(LLMDecision decision);

    /**
     * Find LLM decisions by simulation ID with optional filters and ID-based pagination.
     *
     * @param simulationId simulation unique identifier
     * @param from optional start timestamp filter
     * @param to optional end timestamp filter
     * @param agentId optional agent ID filter
     * @param limit optional maximum number of results
     * @param beforeLLMDecisionId optional: get decisions with ID < this value (for pagination backwards)
     * @param afterLLMDecisionId optional: get decisions with ID > this value (for pagination forwards)
     * @return list of LLM decisions matching the criteria
     */
    List<LLMDecision> findBySimulationId(
        UUID simulationId,
        Instant from,
        Instant to,
        String agentId,
        Integer limit,
        Long beforeLLMDecisionId,
        Long afterLLMDecisionId
    );

    /**
     * Get the most recent decision for a specific agent in a simulation prior to the given timestamp.
     * Used for decision caching to avoid redundant LLM calls.
     *
     * @param agentRole the role of the agent (TECHNICAL, FUNDAMENTAL, etc.)
     * @param simulationId the simulation identifier
     * @param beforeTimestamp only return decisions made before this timestamp
     * @return the most recent decision, or empty if none found
     */
    Optional<LLMDecision> getPreviousDecision(AgentRole agentRole, SimulationId simulationId, Instant beforeTimestamp);

    /**
     * Bulk fetch LLM decisions by their IDs.
     * This method avoids N+1 query problems when fetching multiple decisions.
     *
     * @param llmDecisionIds list of LLM decision IDs to fetch
     * @return list of LLM decisions matching the provided IDs
     */
    List<LLMDecision> findByIds(List<Long> llmDecisionIds);

    /**
     * Count LLM decisions at or after a given timestamp.
     */
    long countBySimulationIdAfter(UUID simulationId, Instant afterInclusive);

    /**
     * Delete LLM decisions at or after a given timestamp.
     * Returns the number of rows deleted.
     */
    long deleteBySimulationIdAfter(UUID simulationId, Instant afterInclusive);

    /**
     * Count agent calls whose recorded prompt-token count cannot be a real measurement of the
     * prompt that was sent.
     *
     * <p>Two cases, both meaning "not counted": zero, and a value far too small for the prompt
     * — local servers have answered {@code prompt_tokens = 2} for a 4,543-character prompt while
     * reporting genuine completion tokens. Because completion tokens ARE recorded, a
     * simulation's prompt-token SUM is never zero, so summing cannot reveal the gap; the prompt
     * text itself is the only reliable reference.</p>
     *
     * <p>Callers reporting token totals must use this to say "unavailable" rather than present a
     * figure that understates the real usage by three orders of magnitude.</p>
     *
     * @param simulationId simulation unique identifier
     * @return number of agent calls with an uncounted or implausible prompt-token value
     */
    long countUncountedPromptTokensBySimulationId(UUID simulationId);
}
