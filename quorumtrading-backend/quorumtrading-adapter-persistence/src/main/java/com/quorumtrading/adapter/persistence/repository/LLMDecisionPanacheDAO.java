package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.LLMDecisionJpaEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LLMDecisionPanacheDAO implements PanacheRepository<LLMDecisionJpaEntity> {

    public List<LLMDecisionJpaEntity> findBySimulationId(
            UUID simulationId,
            Instant from,
            Instant to,
            String agentId,
            Integer limit,
            Long beforeLLMDecisionId,
            Long afterLLMDecisionId) {

        StringBuilder query = new StringBuilder("simulationId = :simulationId");
        Parameters params = Parameters.with("simulationId", simulationId);

        if (from != null) {
            query.append(" AND decisionTimestamp >= :from");
            params.and("from", from);
        }

        if (to != null) {
            query.append(" AND decisionTimestamp <= :to");
            params.and("to", to);
        }

        if (agentId != null && !agentId.isBlank()) {
            // Filter by role column (which contains agent type like TECHNICAL, FUNDAMENTAL, etc.)
            // Note: agentId parameter from API actually refers to the agent's role/type
            query.append(" AND role = :agentId");
            params.and("agentId", agentId);
        }

        // ID-based pagination for navigating through decisions
        if (beforeLLMDecisionId != null) {
            // Get decisions BEFORE this ID (older decisions, chronologically earlier)
            query.append(" AND id < :beforeId");
            params.and("beforeId", beforeLLMDecisionId);
        }

        if (afterLLMDecisionId != null) {
            // Get decisions AFTER this ID (newer decisions, chronologically later)
            query.append(" AND id > :afterId");
            params.and("afterId", afterLLMDecisionId);
        }

        // Order by ID for pagination, otherwise by timestamp
        // ID is auto-incrementing, so higher ID = more recent decision
        if (afterLLMDecisionId != null) {
            // For "next" navigation: get newer decisions, oldest first
            query.append(" ORDER BY id ASC");
        } else {
            // For "previous" navigation or normal query: most recent first
            query.append(" ORDER BY id DESC");
        }

        if (limit != null && limit > 0) {
            return find(query.toString(), params).page(0, limit).list();
        } else {
            // Default limit of 100
            return find(query.toString(), params).page(0, 100).list();
        }
    }

    /**
     * Count LLM decisions at or after a given timestamp.
     */
    public long countBySimulationIdAfter(UUID simulationId, Instant afterInclusive) {
        return count("simulationId = :simulationId AND decisionTimestamp >= :cutoff",
            Parameters.with("simulationId", simulationId).and("cutoff", afterInclusive));
    }

    /**
     * Loosest characters-per-token ratio still treated as a real prompt-token count.
     * Mirrors the guard in the local HTTP adapter; real tokenizers sit near 4 chars per token.
     */
    private static final int MIN_CHARS_PER_PROMPT_TOKEN = 20;

    /**
     * Count agent calls whose prompt-token value cannot be a measurement of the prompt sent.
     *
     * <p>Compares against the stored prompt text rather than against zero: local servers report
     * small nonzero placeholders (2, 4, 10) alongside genuine completion counts, so a
     * zero-check misses them and a sum never reveals them. Catches historical rows too, which a
     * fix at the adapter alone cannot.</p>
     */
    public long countUncountedPromptTokensBySimulationId(UUID simulationId) {
        var query = getEntityManager().createNativeQuery(
            "SELECT count(*) FROM llm_decisions "
            + "WHERE simulation_id = :simulationId "
            + "AND total_tokens > 0 "
            + "AND (prompt_tokens IS NULL "
            + "     OR prompt_tokens = 0 "
            + "     OR (prompt_used IS NOT NULL "
            + "         AND prompt_tokens < length(prompt_used) / :minCharsPerToken))");
        query.setParameter("simulationId", simulationId);
        query.setParameter("minCharsPerToken", MIN_CHARS_PER_PROMPT_TOKEN);
        return ((Number) query.getSingleResult()).longValue();
    }

    /**
     * Delete LLM decisions at or after a given timestamp.
     */
    public long deleteBySimulationIdAfter(UUID simulationId, Instant afterInclusive) {
        return delete("simulationId = :simulationId AND decisionTimestamp >= :cutoff",
            Parameters.with("simulationId", simulationId).and("cutoff", afterInclusive));
    }

    /**
     * Find the most recent decision for a specific agent in a simulation before a given timestamp.
     * Used for decision caching.
     *
     * @param role the agent role (e.g., "TECHNICAL", "FUNDAMENTAL")
     * @param simulationId the simulation UUID
     * @param beforeTimestamp only return decisions made before this timestamp
     * @return the most recent decision, or empty if none found
     */
    public Optional<LLMDecisionJpaEntity> findMostRecentDecision(String role, UUID simulationId, Instant beforeTimestamp) {
        return find("role = :role AND simulationId = :simulationId AND decisionTimestamp < :beforeTimestamp ORDER BY decisionTimestamp DESC",
                Parameters.with("role", role)
                        .and("simulationId", simulationId)
                        .and("beforeTimestamp", beforeTimestamp))
                .firstResultOptional();
    }
}
