package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.ScheduledSimulationJpaEntity;
import com.quorumtrading.model.entity.ScheduledSimulation;
import com.quorumtrading.model.enums.ScheduleFrequency;
import com.quorumtrading.model.valueobject.ScheduledSimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Mapper between ScheduledSimulation domain model and ScheduledSimulationJpaEntity.
 *
 * ARCHITECTURAL NOTE:
 * - Domain ScheduledSimulationId (UUID wrapper) → Database id (UUID)
 * - Domain StrategyId (UUID wrapper) → Database strategy_id (UUID)
 * - Domain ScheduleFrequency (enum) → Database frequency (String)
 */
@ApplicationScoped
public class ScheduledSimulationMapper {

    /**
     * Convert domain ScheduledSimulation to JPA entity.
     */
    public ScheduledSimulationJpaEntity toJpaEntity(ScheduledSimulation scheduledSimulation) {
        ScheduledSimulationJpaEntity entity = new ScheduledSimulationJpaEntity();

        if (scheduledSimulation.getId() != null) {
            entity.id = scheduledSimulation.getId().value();
        }

        entity.setStrategyId(scheduledSimulation.getStrategyId().value());
        entity.setFrequency(scheduledSimulation.getFrequency().name());
        entity.setNextRunAt(scheduledSimulation.getNextRunAt());
        entity.setLastRunAt(scheduledSimulation.getLastRunAt());
        entity.setEnabled(scheduledSimulation.isEnabled());
        entity.setCreatedAt(scheduledSimulation.getCreatedAt());
        entity.setUpdatedAt(scheduledSimulation.getUpdatedAt());

        return entity;
    }

    /**
     * Convert JPA entity to domain ScheduledSimulation.
     */
    public ScheduledSimulation toDomain(ScheduledSimulationJpaEntity entity) {
        ScheduledSimulationId id = ScheduledSimulationId.of(entity.id);
        StrategyId strategyId = new StrategyId(entity.getStrategyId());

        // Parse frequency enum from string (defaults to MANUAL_ONLY if invalid/null)
        ScheduleFrequency frequency;
        try {
            frequency = entity.getFrequency() != null
                ? ScheduleFrequency.valueOf(entity.getFrequency())
                : ScheduleFrequency.MANUAL_ONLY;
        } catch (IllegalArgumentException e) {
            frequency = ScheduleFrequency.MANUAL_ONLY;
        }

        return new ScheduledSimulation(
            id,
            strategyId,
            frequency,
            entity.getNextRunAt(),
            entity.getLastRunAt(),
            entity.isEnabled(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
