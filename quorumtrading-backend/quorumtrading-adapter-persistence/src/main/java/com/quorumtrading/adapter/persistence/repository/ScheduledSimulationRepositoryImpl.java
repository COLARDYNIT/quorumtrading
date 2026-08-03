package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.ScheduledSimulationJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.ScheduledSimulationMapper;
import com.quorumtrading.application.port.out.ScheduledSimulationRepository;
import com.quorumtrading.model.entity.ScheduledSimulation;
import com.quorumtrading.model.valueobject.ScheduledSimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA EntityManager-based implementation of the ScheduledSimulationRepository port.
 *
 * This adapter bridges the domain port interface to the JPA persistence layer.
 * Following hexagonal architecture pattern to maintain clean separation between
 * domain and infrastructure concerns.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class ScheduledSimulationRepositoryImpl implements ScheduledSimulationRepository {

    @Inject
    ScheduledSimulationMapper mapper;

    @Inject
    EntityManager entityManager;

    @Override
    @Transactional
    public void save(ScheduledSimulation scheduledSimulation) {
        ScheduledSimulationJpaEntity entity = mapper.toJpaEntity(scheduledSimulation);

        // Check if entity already exists
        ScheduledSimulationJpaEntity existing = entityManager.find(ScheduledSimulationJpaEntity.class, entity.id);

        if (existing != null) {
            // Update existing entity with current values
            existing.setFrequency(entity.getFrequency());
            existing.setNextRunAt(entity.getNextRunAt());
            existing.setLastRunAt(entity.getLastRunAt());
            existing.setEnabled(entity.isEnabled());
            existing.setUpdatedAt(entity.getUpdatedAt());
            entityManager.merge(existing);
        } else {
            // New entity - persist it
            entityManager.persist(entity);
        }

        entityManager.flush();
    }

    @Override
    public Optional<ScheduledSimulation> findById(ScheduledSimulationId id) {
        ScheduledSimulationJpaEntity entity = entityManager.find(ScheduledSimulationJpaEntity.class, id.value());
        return Optional.ofNullable(entity)
            .map(mapper::toDomain);
    }

    @Override
    public Optional<ScheduledSimulation> findByStrategyId(StrategyId strategyId) {
        List<ScheduledSimulationJpaEntity> entities = entityManager
            .createQuery(
                "SELECT s FROM ScheduledSimulationJpaEntity s WHERE s.strategyId = :strategyId",
                ScheduledSimulationJpaEntity.class
            )
            .setParameter("strategyId", strategyId.value())
            .getResultList();

        return entities.isEmpty()
            ? Optional.empty()
            : Optional.of(mapper.toDomain(entities.get(0)));
    }

    @Override
    public List<ScheduledSimulation> findDueSchedules(Instant now) {
        List<ScheduledSimulationJpaEntity> entities = entityManager
            .createQuery(
                "SELECT s FROM ScheduledSimulationJpaEntity s " +
                "WHERE s.enabled = true " +
                "AND s.nextRunAt IS NOT NULL " +
                "AND s.nextRunAt <= :now " +
                "ORDER BY s.nextRunAt ASC",
                ScheduledSimulationJpaEntity.class
            )
            .setParameter("now", now)
            .getResultList();

        return entities.stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<ScheduledSimulation> findAllEnabled() {
        List<ScheduledSimulationJpaEntity> entities = entityManager
            .createQuery(
                "SELECT s FROM ScheduledSimulationJpaEntity s WHERE s.enabled = true ORDER BY s.createdAt ASC",
                ScheduledSimulationJpaEntity.class
            )
            .getResultList();

        return entities.stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(ScheduledSimulationId id) {
        ScheduledSimulationJpaEntity entity = entityManager.find(ScheduledSimulationJpaEntity.class, id.value());
        if (entity != null) {
            entityManager.remove(entity);
            entityManager.flush();
        }
    }

    @Override
    @Transactional
    public void deleteByStrategyId(StrategyId strategyId) {
        entityManager
            .createQuery("DELETE FROM ScheduledSimulationJpaEntity s WHERE s.strategyId = :strategyId")
            .setParameter("strategyId", strategyId.value())
            .executeUpdate();
        entityManager.flush();
    }
}
