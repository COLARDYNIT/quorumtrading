package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate root representing a market research configuration.
 * Coordinates periodic LLM-driven analysis to discover trading opportunities.
 */
public class MarketResearch {

    private final UUID id;
    private String name;
    private String markdownInstructions;
    private Set<Symbol> targetAssets;
    private ResearchInterval interval;
    private ResearchStatus status;
    private Instant nextScheduledRun;
    private final boolean enableAutoBacktest;
    private final BacktestConfiguration backtestConfig;
    private final Instant createdAt;
    private Instant lastExecutedAt;

    // Ownership field
    private final UserId ownerId;

    // External pause state (e.g., budget depleted)
    private boolean pausedForExternalReason;
    private String externalPauseReason;

    public MarketResearch(
        String name,
        String markdownInstructions,
        Set<Symbol> targetAssets,
        ResearchInterval interval,
        boolean enableAutoBacktest,
        BacktestConfiguration backtestConfig,
        UserId ownerId
    ) {
        this.id = UUID.randomUUID();

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        if (markdownInstructions == null || markdownInstructions.isBlank()) {
            throw new IllegalArgumentException("Markdown instructions cannot be blank");
        }
        // Target assets are optional - LLM can discover relevant assets from instructions
        if (enableAutoBacktest && backtestConfig == null) {
            throw new IllegalArgumentException("Backtest configuration required when auto-backtest enabled");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("OwnerId cannot be null");
        }

        this.name = name;
        this.markdownInstructions = markdownInstructions;
        this.targetAssets = targetAssets != null ? Set.copyOf(targetAssets) : Set.of();
        this.interval = Objects.requireNonNull(interval, "Interval cannot be null");
        this.status = ResearchStatus.ACTIVE;
        this.enableAutoBacktest = enableAutoBacktest;
        this.backtestConfig = backtestConfig;
        this.ownerId = ownerId;
        this.createdAt = Instant.now();
        this.nextScheduledRun = Instant.now(); // Execute immediately on creation
    }

    /**
     * Pause this research (stops scheduled executions).
     */
    public void pause() {
        if (!status.canTransitionTo(ResearchStatus.PAUSED)) {
            throw new IllegalStateException("Cannot transition from " + status + " to PAUSED");
        }
        this.status = ResearchStatus.PAUSED;
    }

    /**
     * Resume this research (restarts scheduled executions).
     */
    public void resume() {
        if (!status.canTransitionTo(ResearchStatus.ACTIVE)) {
            throw new IllegalStateException("Cannot transition from " + status + " to ACTIVE");
        }
        this.status = ResearchStatus.ACTIVE;
        this.nextScheduledRun = calculateNextRun(Instant.now());
    }

    /**
     * Mark this research as completed (terminal state).
     */
    public void complete() {
        this.status = ResearchStatus.COMPLETED;
    }

    /**
     * Mark that this research was executed, update next scheduled run.
     */
    public void markAsExecuted() {
        this.lastExecutedAt = Instant.now();
        this.nextScheduledRun = calculateNextRun(this.lastExecutedAt);
    }

    /**
     * Update the name of this research.
     *
     * @param name the new name (cannot be blank)
     * @throws IllegalArgumentException if name is blank
     */
    public void updateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
        this.name = name;
    }

    /**
     * Update the markdown instructions for this research.
     *
     * @param markdownInstructions the new instructions (cannot be blank)
     * @throws IllegalArgumentException if instructions are blank
     */
    public void updateMarkdownInstructions(String markdownInstructions) {
        if (markdownInstructions == null || markdownInstructions.isBlank()) {
            throw new IllegalArgumentException("Markdown instructions cannot be blank");
        }
        this.markdownInstructions = markdownInstructions;
    }

    /**
     * Update the target assets for this research.
     * Target assets are optional - LLM can discover relevant assets from instructions.
     *
     * @param targetAssets the new target assets (can be empty or null)
     */
    public void updateTargetAssets(Set<Symbol> targetAssets) {
        this.targetAssets = targetAssets != null ? Set.copyOf(targetAssets) : Set.of();
    }

    /**
     * Update the research interval.
     *
     * @param interval the new interval (cannot be null)
     * @throws NullPointerException if interval is null
     */
    public void updateInterval(ResearchInterval interval) {
        Objects.requireNonNull(interval, "Interval cannot be null");
        this.interval = interval;
        // Recalculate next scheduled run based on new interval
        this.nextScheduledRun = calculateNextRun(Instant.now());
    }

    /**
     * Calculate next scheduled run based on interval.
     */
    private Instant calculateNextRun(Instant from) {
        return switch (interval.unit()) {
            case DAYS -> from.plus(Duration.ofDays(interval.amount()));
            case WEEKS -> from.plus(Duration.ofDays(interval.amount() * 7L));
            case MONTHS -> from.plus(Duration.ofDays(interval.amount() * 30L)); // Approximate
        };
    }

    // Getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getMarkdownInstructions() { return markdownInstructions; }
    public Set<Symbol> getTargetAssets() { return targetAssets; }
    public ResearchInterval getInterval() { return interval; }
    public ResearchStatus getStatus() { return status; }
    public Instant getNextScheduledRun() { return nextScheduledRun; }
    public boolean isEnableAutoBacktest() { return enableAutoBacktest; }
    public BacktestConfiguration getBacktestConfig() { return backtestConfig; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastExecutedAt() { return lastExecutedAt; }
    public UserId getOwnerId() { return ownerId; }

    // ========== External Pause Management ==========

    /**
     * Check if this research can execute (considering both status and external pause).
     */
    public boolean canExecute() {
        return status == ResearchStatus.ACTIVE && !pausedForExternalReason;
    }

    /**
     * Pause this research due to an external reason (e.g., budget depleted).
     * This is separate from the user-initiated pause.
     */
    public void pauseForExternalReason(String reason) {
        this.pausedForExternalReason = true;
        this.externalPauseReason = reason;
    }

    /**
     * Resume from external pause (e.g., budget restored).
     */
    public void resumeFromExternalPause() {
        this.pausedForExternalReason = false;
        this.externalPauseReason = null;
    }

    /**
     * Check if this research is paused due to an external reason.
     */
    public boolean isPausedForExternalReason() {
        return pausedForExternalReason;
    }

    /**
     * Get the external pause reason, if any.
     */
    public String getExternalPauseReason() {
        return externalPauseReason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MarketResearch that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
