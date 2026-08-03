package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.StrategyJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.StrategyMapper;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA EntityManager-based implementation of the StrategyRepository port.
 *
 * This adapter bridges the domain port interface to the JPA persistence layer.
 * Now that database uses UUID primary keys, mapping is direct between domain and database.
 *
 * Uses EntityManager instead of Panache static methods to support cross-module entity discovery in Quarkus.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class StrategyRepositoryImpl implements StrategyRepository {

    @Inject
    StrategyMapper mapper;

    @Inject
    EntityManager entityManager;

    @Override
    @Transactional
    public Strategy save(Strategy strategy) {
        StrategyJpaEntity entity = mapper.toJpaEntity(strategy);

        // Ensure entity always has an ID (since we removed @GeneratedValue)
        if (entity.id == null) {
            entity.id = UUID.randomUUID();
        }

        // Check if this is an update (entity with this ID exists)
        StrategyJpaEntity existing = entityManager.find(StrategyJpaEntity.class, entity.id);

        if (existing != null) {
            // Update existing strategy - modify the existing entity directly
            existing.setMarkdownContent(entity.getMarkdownContent());
            existing.setName(entity.getName());
            existing.setStatus(entity.getStatus());
            existing.setEnabled(entity.isEnabled());
            existing.setPublicVisible(entity.isPublicVisible());
            // Carried on update for the same reason it is carried on insert — otherwise a
            // description set at creation is wiped by the next save of that strategy.
            existing.setDescription(entity.getDescription());
            // optLockVersion is automatically incremented by @Version annotation
            // updatedAt is automatically set by database trigger
            entityManager.merge(existing);
            // Return the existing entity which now has the updated version
            entity = existing;
        } else {
            // New strategy - use native SQL INSERT to bypass Hibernate's entity state checks
            // This is necessary because Hibernate considers entities with IDs as "detached"
            // even if they don't exist in the database yet
            entityManager.createNativeQuery(
                // description belongs in this list: the column exists and the domain sets it, but
                // this hand-written INSERT omitted it, so every description supplied through
                // create_strategy (MCP or REST) was silently discarded and read back as null.
                "INSERT INTO orchestrator_strategies (id, name, markdown_content, status, opt_lock_version, created_at, created_by, " +
                "owner_id, enabled, generation, test_frequency, public_visible, parent_strategy_id, description) " +
                "VALUES (?1, ?2, ?3, ?4, 0, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)"
            )
            .setParameter(1, entity.id)
            .setParameter(2, entity.getName())
            .setParameter(3, entity.getMarkdownContent())
            .setParameter(4, entity.getStatus() != null ? entity.getStatus() : "ACTIVE")
            .setParameter(5, java.time.Instant.now())
            .setParameter(6, "system")
            .setParameter(7, entity.getOwnerId())
            .setParameter(8, entity.isEnabled())
            .setParameter(9, entity.getGeneration() != null ? entity.getGeneration() : 0)
            .setParameter(10, entity.getTestFrequency() != null ? entity.getTestFrequency() : "MANUAL_ONLY")
            .setParameter(11, entity.isPublicVisible())
            .setParameter(12, entity.getParentStrategyId())
            .setParameter(13, entity.getDescription())
            .executeUpdate();

            // Refresh entity to get managed state and DB-generated values
            entity = entityManager.find(StrategyJpaEntity.class, entity.id);
        }

        return mapper.toDomain(entity);
    }

    @Override
    public Optional<Strategy> findById(StrategyId id) {
        StrategyJpaEntity entity = entityManager.find(StrategyJpaEntity.class, id.value());
        return entity == null ? Optional.empty() : Optional.of(mapper.toDomain(entity));
    }

    @Override
    public List<Strategy> findByIds(Collection<StrategyId> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<UUID> uuids = ids.stream()
            .map(StrategyId::value)
            .distinct()
            .collect(Collectors.toList());
        TypedQuery<StrategyJpaEntity> query = entityManager.createQuery(
            "SELECT s FROM StrategyJpaEntity s WHERE s.id IN :ids",
            StrategyJpaEntity.class
        );
        query.setParameter("ids", uuids);
        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Strategy> findAll() {
        TypedQuery<StrategyJpaEntity> query = entityManager.createQuery(
            "SELECT s FROM StrategyJpaEntity s",
            StrategyJpaEntity.class
        );

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean deleteById(StrategyId id) {
        StrategyJpaEntity entity = entityManager.find(StrategyJpaEntity.class, id.value());

        if (entity != null) {
            entityManager.remove(entity);
            entityManager.flush(); // Flush immediately to trigger DB cascade deletes
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public int deleteAll() {
        // Use JPQL to delete all strategies
        // CASCADE DELETE constraints will automatically delete all associated data
        return entityManager.createQuery("DELETE FROM StrategyJpaEntity")
            .executeUpdate();
    }

    @Override
    public boolean existsById(StrategyId id) {
        return entityManager.find(StrategyJpaEntity.class, id.value()) != null;
    }

    @Override
    public List<Strategy> findByOwnerId(UserId ownerId) {
        TypedQuery<StrategyJpaEntity> query = entityManager.createQuery(
            "SELECT s FROM StrategyJpaEntity s WHERE s.ownerId = :ownerId ORDER BY s.createdAt DESC",
            StrategyJpaEntity.class
        );
        query.setParameter("ownerId", ownerId.value());

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Strategy> findAllPublic() {
        TypedQuery<StrategyJpaEntity> query = entityManager.createQuery(
            "SELECT s FROM StrategyJpaEntity s WHERE s.publicVisible = true AND s.status = 'ACTIVE' ORDER BY s.name ASC",
            StrategyJpaEntity.class
        );
        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public long count() {
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(s) FROM StrategyJpaEntity s",
            Long.class
        );
        return query.getSingleResult();
    }
}
