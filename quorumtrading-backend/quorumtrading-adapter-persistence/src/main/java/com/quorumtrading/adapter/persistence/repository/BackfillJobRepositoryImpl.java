package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.BackfillJobJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.BackfillJobMapper;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.model.enums.BackfillStatus;
import com.quorumtrading.model.marketdata.BackfillJob;
import com.quorumtrading.model.valueobject.SimulationId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA EntityManager-based implementation of the BackfillJobRepository port.
 *
 * This adapter bridges the domain port interface to the JPA persistence layer.
 * Following hexagonal architecture pattern to maintain clean separation between
 * domain and infrastructure concerns.
 *
 * Uses EntityManager for persistence operations to support cross-module entity discovery in Quarkus.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class BackfillJobRepositoryImpl implements BackfillJobRepository {

    @Inject
    BackfillJobMapper mapper;

    @Inject
    EntityManager entityManager;

    @Override
    @Transactional
    public void save(BackfillJob request) {
        BackfillJobJpaEntity entity = mapper.toEntity(request);

        // Check if entity already exists
        BackfillJobJpaEntity existing = entityManager.find(BackfillJobJpaEntity.class, entity.getId());

        if (existing != null) {
            // Update existing entity with current values
            existing.setStatus(entity.getStatus());
            existing.setProgressPercentage(entity.getProgressPercentage());
            existing.setStartedAt(entity.getStartedAt());
            existing.setCompletedAt(entity.getCompletedAt());
            existing.setErrorMessage(entity.getErrorMessage());
            existing.setTotalRecordsExpected(entity.getTotalRecordsExpected());
            existing.setRecordsProcessed(entity.getRecordsProcessed());
            existing.setRetryAfter(entity.getRetryAfter());
            entityManager.merge(existing);
        } else {
            // New entity - persist it
            entityManager.persist(entity);
        }

        // Flush to ensure changes are written to database
        entityManager.flush();
    }

    @Override
    @Transactional
    public void update(BackfillJob request) {
        // Update is same as save for this implementation
        save(request);
    }

    @Override
    public Optional<BackfillJob> findById(UUID id) {
        BackfillJobJpaEntity entity = entityManager.find(BackfillJobJpaEntity.class, id);
        return Optional.ofNullable(entity)
            .map(mapper::toDomain);
    }

    @Override
    public List<BackfillJob> findPendingJobs() {
        List<BackfillJobJpaEntity> entities = entityManager
            .createQuery(
                "SELECT b FROM BackfillJobJpaEntity b WHERE b.status IN :statuses ORDER BY b.createdAt ASC",
                BackfillJobJpaEntity.class
            )
            .setParameter("statuses", List.of(
                BackfillStatus.PENDING.name(),
                BackfillStatus.IN_PROGRESS.name(),
                BackfillStatus.RETRY_PENDING.name()
            ))
            .getResultList();

        return entities.stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<BackfillJob> findByLinkedSimulationId(SimulationId simulationId) {
        try {
            BackfillJobJpaEntity entity = entityManager
                .createQuery(
                    "SELECT b FROM BackfillJobJpaEntity b WHERE b.linkedSimulationId = :simulationId " +
                    "ORDER BY b.createdAt DESC",
                    BackfillJobJpaEntity.class
                )
                .setParameter("simulationId", simulationId.value())
                .setMaxResults(1)
                .getSingleResult();

            return Optional.of(mapper.toDomain(entity));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}
