package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.MarketResearchJpaEntity;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.valueobject.ResearchStatus;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA EntityManager-based implementation of the MarketResearchRepository port.
 *
 * This adapter bridges the domain port interface to the JPA persistence layer.
 * Uses EntityManager instead of Panache static methods to support cross-module entity discovery in Quarkus.
 *
 * Conversion between domain and JPA entities is handled by MarketResearchJpaEntity's
 * fromDomain() and toDomain() methods.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class JpaMarketResearchAdapter implements MarketResearchRepository {

    @Inject
    EntityManager entityManager;

    @Override
    @Transactional
    public MarketResearch save(MarketResearch research) {
        MarketResearchJpaEntity entity = MarketResearchJpaEntity.fromDomain(research);

        // Ensure entity always has an ID
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }

        // Check if this is an update (entity with this ID exists)
        MarketResearchJpaEntity existing = entityManager.find(MarketResearchJpaEntity.class, entity.getId());

        if (existing != null) {
            // Update existing research - modify the existing entity directly
            existing.setName(entity.getName());
            existing.setDescription(entity.getDescription());
            existing.setTargetAssets(entity.getTargetAssets());
            existing.setIntervalAmount(entity.getIntervalAmount());
            existing.setIntervalUnit(entity.getIntervalUnit());
            existing.setStatus(entity.getStatus());
            existing.setAutoBacktest(entity.isAutoBacktest());
            existing.setBacktestLengthSeconds(entity.getBacktestLengthSeconds());
            existing.setBacktestPeriodStart(entity.getBacktestPeriodStart());
            existing.setBacktestPeriodEnd(entity.getBacktestPeriodEnd());
            existing.setLastExecutedAt(entity.getLastExecutedAt());
            existing.setNextScheduledRun(entity.getNextScheduledRun());
            existing.setPausedForExternalReason(entity.isPausedForExternalReason());
            existing.setExternalPauseReason(entity.getExternalPauseReason());
            entityManager.merge(existing);
            entity = existing;
        } else {
            // New research - persist it
            entityManager.persist(entity);
        }

        return entity.toDomain();
    }

    @Override
    public Optional<MarketResearch> findById(UUID id) {
        MarketResearchJpaEntity entity = entityManager.find(MarketResearchJpaEntity.class, id);
        return entity == null ? Optional.empty() : Optional.of(entity.toDomain());
    }

    @Override
    public List<MarketResearch> findAll() {
        TypedQuery<MarketResearchJpaEntity> query = entityManager.createQuery(
            "SELECT m FROM MarketResearchJpaEntity m",
            MarketResearchJpaEntity.class
        );

        return query.getResultList()
            .stream()
            .map(MarketResearchJpaEntity::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<MarketResearch> findByStatus(ResearchStatus status) {
        TypedQuery<MarketResearchJpaEntity> query = entityManager.createQuery(
            "SELECT m FROM MarketResearchJpaEntity m WHERE m.status = :status",
            MarketResearchJpaEntity.class
        );
        query.setParameter("status", status.name());

        return query.getResultList()
            .stream()
            .map(MarketResearchJpaEntity::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<MarketResearch> findDueForExecution(Instant now) {
        TypedQuery<MarketResearchJpaEntity> query = entityManager.createQuery(
            // pausedForExternalReason: a system pause (budget depleted) leaves status ACTIVE, so
            // without this the scheduler re-ran budget-paused research every hour.
            "SELECT m FROM MarketResearchJpaEntity m WHERE m.status = :status AND m.nextScheduledRun <= :now"
                + " AND m.pausedForExternalReason = false",
            MarketResearchJpaEntity.class
        );
        query.setParameter("status", ResearchStatus.ACTIVE.name());
        query.setParameter("now", now);

        return query.getResultList()
            .stream()
            .map(MarketResearchJpaEntity::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        MarketResearchJpaEntity entity = entityManager.find(MarketResearchJpaEntity.class, id);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }

    @Override
    public boolean existsById(UUID id) {
        return entityManager.find(MarketResearchJpaEntity.class, id) != null;
    }

    @Override
    public List<MarketResearch> findByOwnerId(UserId ownerId) {
        TypedQuery<MarketResearchJpaEntity> query = entityManager.createQuery(
            "SELECT m FROM MarketResearchJpaEntity m WHERE m.ownerId = :ownerId ORDER BY m.createdAt DESC",
            MarketResearchJpaEntity.class
        );
        query.setParameter("ownerId", ownerId.value());

        return query.getResultList()
            .stream()
            .map(MarketResearchJpaEntity::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<MarketResearch> findActiveByOwnerId(UserId ownerId) {
        TypedQuery<MarketResearchJpaEntity> query = entityManager.createQuery(
            "SELECT m FROM MarketResearchJpaEntity m WHERE m.ownerId = :ownerId AND m.status = :status",
            MarketResearchJpaEntity.class
        );
        query.setParameter("ownerId", ownerId.value());
        query.setParameter("status", ResearchStatus.ACTIVE.name());

        return query.getResultList()
            .stream()
            .map(MarketResearchJpaEntity::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<MarketResearch> findPausedForExternalReasonByOwnerId(UserId ownerId) {
        TypedQuery<MarketResearchJpaEntity> query = entityManager.createQuery(
            "SELECT m FROM MarketResearchJpaEntity m WHERE m.ownerId = :ownerId AND m.pausedForExternalReason = true",
            MarketResearchJpaEntity.class
        );
        query.setParameter("ownerId", ownerId.value());

        return query.getResultList()
            .stream()
            .map(MarketResearchJpaEntity::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public long count() {
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(m) FROM MarketResearchJpaEntity m",
            Long.class
        );
        return query.getSingleResult();
    }
}
