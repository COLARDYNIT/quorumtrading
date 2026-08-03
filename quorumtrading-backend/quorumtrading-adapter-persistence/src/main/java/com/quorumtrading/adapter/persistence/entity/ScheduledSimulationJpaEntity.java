package com.quorumtrading.adapter.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for ScheduledSimulation persistence.
 *
 * Maps to the scheduled_simulations table created by V40 migration.
 * This is SEPARATE from the domain ScheduledSimulation to maintain hexagonal architecture boundaries.
 */
@Entity
@Table(name = "scheduled_simulations")
public class ScheduledSimulationJpaEntity extends PanacheEntityBase {

    /**
     * UUID primary key.
     * Must be set by application code before persist.
     */
    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    /**
     * Reference to the strategy this schedule is for.
     */
    @Column(name = "strategy_id", nullable = false)
    private UUID strategyId;

    /**
     * Schedule frequency (DAILY, WEEKLY, MANUAL_ONLY).
     */
    @Column(name = "frequency", length = 20, nullable = false)
    private String frequency;

    /**
     * When the next run is scheduled.
     */
    @Column(name = "next_run_at")
    private Instant nextRunAt;

    /**
     * When the last run was executed.
     */
    @Column(name = "last_run_at")
    private Instant lastRunAt;

    /**
     * Whether this schedule is enabled.
     */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /**
     * Creation timestamp.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Last update timestamp.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Set default values before persisting new entity.
     */
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (frequency == null) {
            frequency = "MANUAL_ONLY";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters

    public UUID getStrategyId() {
        return strategyId;
    }

    public void setStrategyId(UUID strategyId) {
        this.strategyId = strategyId;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(Instant nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Instant lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
