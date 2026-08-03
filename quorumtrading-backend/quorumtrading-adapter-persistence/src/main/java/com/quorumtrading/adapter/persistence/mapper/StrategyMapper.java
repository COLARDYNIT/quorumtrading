package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.StrategyJpaEntity;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.ScheduleFrequency;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Mapper between Strategy domain model and StrategyJpaEntity.
 *
 * ARCHITECTURAL NOTE:
 * - Domain StrategyId (UUID wrapper) → Database id (UUID)
 * - Domain name → Database name field
 * - Domain version (Integer for optimistic locking) → Database optLockVersion field
 * - Domain content → Database markdownContent
 *
 * The opt_lock_version field provides optimistic locking to prevent concurrent modification conflicts.
 */
@ApplicationScoped
public class StrategyMapper {

    /**
     * Convert domain Strategy to JPA entity.
     *
     * Maps:
     * - StrategyId (UUID) → id field (direct mapping)
     * - name → name field
     * - version (Integer) → optLockVersion (for optimistic locking)
     * - content → markdownContent
     * - status (enum) → status (string)
     *
     * Timestamps are set to defaults by @PrePersist.
     */
    public StrategyJpaEntity toJpaEntity(Strategy strategy) {
        StrategyJpaEntity entity = new StrategyJpaEntity();

        // Map UUID directly to entity ID
        if (strategy.getId() != null) {
            entity.id = strategy.getId().value();
        }
        // If id is null, JPA will generate a new UUID via @GeneratedValue

        entity.setName(strategy.getName());
        entity.setDescription(strategy.getDescription());
        entity.setMarkdownContent(strategy.getContent());
        entity.setOptLockVersion(strategy.getVersion());
        entity.setStatus(strategy.getStatus() != null ? strategy.getStatus().name() : "ACTIVE");

        // createdAt/createdBy are set by @PrePersist if null
        if (strategy.getCreatedAt() != null) {
            entity.setCreatedAt(strategy.getCreatedAt());
        }
        if (strategy.getUpdatedAt() != null) {
            entity.setUpdatedAt(strategy.getUpdatedAt());
        }

        // Lineage fields
        if (strategy.getParentStrategyId() != null) {
            entity.setParentStrategyId(strategy.getParentStrategyId().value());
        }
        entity.setGeneration(strategy.getGeneration());
        entity.setLastTestedAt(strategy.getLastTestedAt());
        entity.setTestFrequency(strategy.getTestFrequency() != null
            ? strategy.getTestFrequency().name()
            : "MANUAL_ONLY");

        // Phase 4: Auto-disable support
        entity.setEnabled(strategy.isEnabled());

        // Public visibility
        entity.setPublicVisible(strategy.isPublicVisible());

        // Ownership field
        if (strategy.getOwnerId() != null) {
            entity.setOwnerId(strategy.getOwnerId().value());
        }

        return entity;
    }

    /**
     * Convert JPA entity to domain Strategy.
     *
     * Maps:
     * - id field (UUID) → StrategyId
     * - name field → name
     * - optLockVersion → version (Integer for optimistic locking)
     * - markdownContent → content
     * - status (string) → status (enum)
     */
    public Strategy toDomain(StrategyJpaEntity entity) {
        // Direct UUID mapping
        StrategyId id = new StrategyId(entity.id);

        // Parse status enum from string (defaults to ACTIVE if invalid/null)
        Strategy.StrategyStatus status;
        try {
            status = entity.getStatus() != null
                ? Strategy.StrategyStatus.valueOf(entity.getStatus())
                : Strategy.StrategyStatus.ACTIVE;
        } catch (IllegalArgumentException e) {
            status = Strategy.StrategyStatus.ACTIVE;
        }

        // Parse test frequency enum from string (defaults to MANUAL_ONLY if invalid/null)
        ScheduleFrequency testFrequency;
        try {
            testFrequency = entity.getTestFrequency() != null
                ? ScheduleFrequency.valueOf(entity.getTestFrequency())
                : ScheduleFrequency.MANUAL_ONLY;
        } catch (IllegalArgumentException e) {
            testFrequency = ScheduleFrequency.MANUAL_ONLY;
        }

        // Map parentStrategyId (nullable)
        StrategyId parentStrategyId = entity.getParentStrategyId() != null
            ? new StrategyId(entity.getParentStrategyId())
            : null;

        // Map ownerId (required)
        UserId ownerId = entity.getOwnerId() != null
            ? new UserId(entity.getOwnerId())
            : null;

        return new Strategy(
            id,
            entity.getName(),
            entity.getDescription(),
            entity.getOptLockVersion(),
            entity.getMarkdownContent(),
            status,
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            parentStrategyId,
            entity.getGeneration() != null ? entity.getGeneration() : 0,
            entity.getLastTestedAt(),
            testFrequency,
            entity.isEnabled(),
            ownerId,
            entity.isPublicVisible()
        );
    }
}
