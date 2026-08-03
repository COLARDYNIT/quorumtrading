package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.ScheduleFrequency;
import com.quorumtrading.model.valueobject.ScheduledSimulationId;
import com.quorumtrading.model.valueobject.StrategyId;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity for scheduled simulation configuration.
 *
 * <p><b>Aggregate Root</b> - Manages recurring simulation execution.</p>
 */
public class ScheduledSimulation implements AggregateRoot {

    private final ScheduledSimulationId id;
    private final StrategyId strategyId;
    private ScheduleFrequency frequency;
    private Instant nextRunAt;
    private Instant lastRunAt;
    private boolean enabled;
    private final Instant createdAt;
    private Instant updatedAt;

    public ScheduledSimulation(
            ScheduledSimulationId id,
            StrategyId strategyId,
            ScheduleFrequency frequency) {
        this.id = Objects.requireNonNull(id);
        this.strategyId = Objects.requireNonNull(strategyId);
        this.frequency = Objects.requireNonNull(frequency);
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Repository constructor with all fields
    public ScheduledSimulation(
            ScheduledSimulationId id,
            StrategyId strategyId,
            ScheduleFrequency frequency,
            Instant nextRunAt,
            Instant lastRunAt,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.strategyId = strategyId;
        this.frequency = frequency;
        this.nextRunAt = nextRunAt;
        this.lastRunAt = lastRunAt;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Check if this schedule is due for execution.
     */
    public boolean isDue(Instant now) {
        return enabled && nextRunAt != null && !now.isBefore(nextRunAt);
    }

    /**
     * Record that a simulation run was executed.
     */
    public void recordRun(Instant completedAt) {
        this.lastRunAt = completedAt;
        this.nextRunAt = frequency.calculateNextRun(completedAt);
        this.updatedAt = Instant.now();
    }

    /**
     * Enable scheduling for this simulation.
     */
    public void enable() {
        this.enabled = true;
        this.updatedAt = Instant.now();
    }

    /**
     * Disable scheduling for this simulation.
     */
    public void disable() {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

    /**
     * Update the schedule frequency.
     */
    public void updateFrequency(ScheduleFrequency newFrequency) {
        this.frequency = Objects.requireNonNull(newFrequency);
        this.nextRunAt = newFrequency.calculateNextRun(Instant.now());
        this.updatedAt = Instant.now();
    }

    // Getters
    public ScheduledSimulationId getId() { return id; }
    public StrategyId getStrategyId() { return strategyId; }
    public ScheduleFrequency getFrequency() { return frequency; }
    public Instant getNextRunAt() { return nextRunAt; }
    public Instant getLastRunAt() { return lastRunAt; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Setters for testing/construction
    public void setNextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScheduledSimulation that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
