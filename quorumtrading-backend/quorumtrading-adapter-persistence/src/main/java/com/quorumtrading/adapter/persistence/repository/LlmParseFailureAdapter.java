package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.LlmParseFailureJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.LlmParseFailureMapper;
import com.quorumtrading.application.port.out.LlmParseFailureRepository;
import com.quorumtrading.application.usecase.dto.ModelReliabilityStats;
import com.quorumtrading.model.entity.LlmParseFailure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persists and queries LLM parse failures for diagnostic purposes.
 * Save uses REQUIRES_NEW so the write survives even if the calling transaction rolls back.
 */
@ApplicationScoped
public class LlmParseFailureAdapter implements LlmParseFailureRepository {

    @Inject
    EntityManager entityManager;

    @Inject
    LlmParseFailureMapper mapper;

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void save(LlmParseFailure failure) {
        entityManager.persist(mapper.toJpaEntity(failure));
    }

    @Override
    public List<LlmParseFailure> findBySimulationIdAndTimestamps(UUID simulationId, List<Instant> timestamps) {
        if (timestamps == null || timestamps.isEmpty()) {
            return List.of();
        }
        List<LlmParseFailureJpaEntity> entities = entityManager.createQuery(
                "SELECT e FROM LlmParseFailureJpaEntity e WHERE e.simulationId = :simId AND e.decisionTimestamp IN :timestamps",
                LlmParseFailureJpaEntity.class)
            .setParameter("simId", simulationId)
            .setParameter("timestamps", timestamps)
            .getResultList();
        return entities.stream().map(mapper::toDomain).toList();
    }

    @Override
    public ModelReliabilityStats getModelReliabilityStats(int days) {
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);

        String sql = """
            SELECT model_name,
                   SUM(success_count) AS successes,
                   SUM(failure_count) AS failures
            FROM (
                SELECT llm_model AS model_name, COUNT(*) AS success_count, 0 AS failure_count
                FROM llm_decisions
                WHERE decision_timestamp > :cutoff
                GROUP BY llm_model
                UNION ALL
                SELECT actual_model AS model_name, 0 AS success_count, COUNT(*) AS failure_count
                FROM llm_parse_failures
                WHERE created_at > :cutoff
                GROUP BY actual_model
            ) combined
            GROUP BY model_name
            ORDER BY model_name
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("cutoff", cutoff);

        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        List<ModelReliabilityStats.ModelReliabilityEntry> entries = new ArrayList<>();
        for (Object[] row : results) {
            String modelId = (String) row[0];
            if (modelId == null || modelId.isBlank()) continue;

            long successes = ((Number) row[1]).longValue();
            long failures = ((Number) row[2]).longValue();
            long total = successes + failures;
            double failureRate = total > 0 ? (double) failures / total : 0.0;

            entries.add(new ModelReliabilityStats.ModelReliabilityEntry(modelId, successes, failures, failureRate));
        }

        return new ModelReliabilityStats(entries);
    }
}
