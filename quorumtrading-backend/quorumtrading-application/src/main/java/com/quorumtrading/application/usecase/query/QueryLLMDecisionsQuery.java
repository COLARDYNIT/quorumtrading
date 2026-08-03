package com.quorumtrading.application.usecase.query;

import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Query for retrieving LLM decisions for a simulation.
 *
 * @param simulationId simulation unique identifier
 * @param from optional start timestamp filter
 * @param to optional end timestamp filter
 * @param agentId optional agent ID filter
 * @param limit optional maximum number of results
 * @param beforeLLMDecisionId optional: get decisions with ID < this value (for pagination backwards)
 * @param afterLLMDecisionId optional: get decisions with ID > this value (for pagination forwards)
 * @param requestingUserId the user asking; ownership resolves through the parent simulation.
 *                         Never null — a null user is denied by the authorization layer.
 */
public record QueryLLMDecisionsQuery(
    UUID simulationId,
    Instant from,
    Instant to,
    String agentId,
    Integer limit,
    Long beforeLLMDecisionId,
    Long afterLLMDecisionId,
    UserId requestingUserId
) {
}
