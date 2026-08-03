package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.ResearchExecutionJpaEntity;
import com.quorumtrading.application.port.out.ResearchExecutionRepository;
import com.quorumtrading.model.entity.ResearchExecution;
import com.quorumtrading.model.valueobject.ResearchOutcome;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA EntityManager-based implementation of the ResearchExecutionRepository port.
 *
 * This adapter bridges the domain port interface to the JPA persistence layer.
 * Uses EntityManager instead of Panache static methods to support cross-module entity discovery in Quarkus.
 *
 * Conversion between domain and JPA entities is handled by ResearchExecutionJpaEntity's
 * fromDomain() and toDomain() methods.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class JpaResearchExecutionAdapter implements ResearchExecutionRepository {

    @Inject
    EntityManager entityManager;

    @Override
    @Transactional
    public ResearchExecution save(ResearchExecution execution) {
        ResearchExecutionJpaEntity entity = ResearchExecutionJpaEntity.fromDomain(execution);

        // Ensure entity always has an ID
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }

        // Check if this is an update (entity with this ID exists)
        ResearchExecutionJpaEntity existing = entityManager.find(ResearchExecutionJpaEntity.class, entity.getId());

        if (existing != null) {
            // Update existing execution - modify the existing entity directly
            existing.setMarketResearchId(entity.getMarketResearchId());
            existing.setOutcome(entity.getOutcome());
            existing.setOutcomeReasoning(entity.getOutcomeReasoning());
            existing.setViabilityScore(entity.getViabilityScore());
            existing.setCreatedStrategyId(entity.getCreatedStrategyId());
            existing.setSimulationId(entity.getSimulationId());
            existing.setExecutedAt(entity.getExecutedAt());
            entityManager.merge(existing);
            entity = existing;
        } else {
            // New execution - persist it
            entityManager.persist(entity);
        }

        return entity.toDomain();
    }

    @Override
    public Optional<ResearchExecution> findById(UUID id) {
        ResearchExecutionJpaEntity entity = entityManager.find(ResearchExecutionJpaEntity.class, id);
        return entity == null ? Optional.empty() : Optional.of(entity.toDomain());
    }

    @Override
    public List<ResearchExecution> findByMarketResearchId(UUID marketResearchId) {
        TypedQuery<ResearchExecutionJpaEntity> query = entityManager.createQuery(
            "SELECT e FROM ResearchExecutionJpaEntity e WHERE e.marketResearchId = :marketResearchId",
            ResearchExecutionJpaEntity.class
        );
        query.setParameter("marketResearchId", marketResearchId);

        return query.getResultList()
            .stream()
            .map(ResearchExecutionJpaEntity::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<ResearchExecution> findByOutcome(ResearchOutcome outcome) {
        TypedQuery<ResearchExecutionJpaEntity> query = entityManager.createQuery(
            "SELECT e FROM ResearchExecutionJpaEntity e WHERE e.outcome = :outcome",
            ResearchExecutionJpaEntity.class
        );
        query.setParameter("outcome", outcome.name());

        return query.getResultList()
            .stream()
            .map(ResearchExecutionJpaEntity::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<ResearchExecution> findLatestByMarketResearchId(UUID marketResearchId) {
        TypedQuery<ResearchExecutionJpaEntity> query = entityManager.createQuery(
            "SELECT e FROM ResearchExecutionJpaEntity e WHERE e.marketResearchId = :marketResearchId ORDER BY e.executedAt DESC",
            ResearchExecutionJpaEntity.class
        );
        query.setParameter("marketResearchId", marketResearchId);
        query.setMaxResults(1);

        List<ResearchExecutionJpaEntity> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0).toDomain());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        ResearchExecutionJpaEntity entity = entityManager.find(ResearchExecutionJpaEntity.class, id);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }

    @Override
    public int countStrategiesCreatedByResearch(UUID marketResearchId) {
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(e) FROM ResearchExecutionJpaEntity e WHERE e.marketResearchId = :marketResearchId AND e.createdStrategyId IS NOT NULL",
            Long.class
        );
        query.setParameter("marketResearchId", marketResearchId);
        return query.getSingleResult().intValue();
    }
}
