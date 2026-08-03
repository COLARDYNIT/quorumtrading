package com.quorumtrading.adapter.persistence.entity;

import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.*;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JPA entity for MarketResearch persistence.
 *
 * Maps to the market_research table created by Flyway migration V33.
 * This is SEPARATE from the domain MarketResearch to maintain hexagonal architecture boundaries.
 *
 * Uses standard JPA (not Panache) for cross-module compatibility.
 */
@Entity
@Table(name = "market_research")
public class MarketResearchJpaEntity {

    /**
     * Primary key (UUID).
     */
    @Id
    @Column(name = "id")
    private UUID id;

    /**
     * Human-readable name for the research configuration.
     */
    @Column(name = "name", length = 255, nullable = false)
    private String name;

    /**
     * Markdown instructions describing the research purpose.
     * Maps to domain's markdownInstructions field.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Comma-separated list of target asset symbols (e.g., "BTC-USD,ETH-USD").
     * Can be empty if LLM should discover assets from instructions.
     */
    @Column(name = "target_assets", columnDefinition = "TEXT")
    private String targetAssets;

    /**
     * Numeric value for the research interval (e.g., 7 for "7 days").
     */
    @Column(name = "interval_amount", nullable = false)
    private Integer intervalAmount;

    /**
     * Unit of time for the interval (DAYS, WEEKS, or MONTHS).
     */
    @Column(name = "interval_unit", length = 20, nullable = false)
    private String intervalUnit;

    /**
     * Current status (ACTIVE, PAUSED, or COMPLETED).
     */
    @Column(name = "status", length = 20, nullable = false)
    private String status;

    /**
     * Whether to automatically run backtests on created strategies.
     */
    @Column(name = "auto_backtest", nullable = false)
    private boolean autoBacktest;

    /**
     * Duration of backtests in seconds (null if auto_backtest is false).
     */
    @Column(name = "backtest_length_seconds")
    private Long backtestLengthSeconds;

    /**
     * Historical start date for backtests (null if auto_backtest is false or not specified).
     */
    @Column(name = "backtest_period_start")
    private Instant backtestPeriodStart;

    /**
     * Historical end date for backtests (null if auto_backtest is false or not specified).
     */
    @Column(name = "backtest_period_end")
    private Instant backtestPeriodEnd;

    /**
     * Timestamp when the research configuration was created.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Timestamp of the most recent execution (null if never executed).
     */
    @Column(name = "last_executed_at")
    private Instant lastExecutedAt;

    /**
     * Timestamp when the next execution is scheduled.
     */
    @Column(name = "next_scheduled_run")
    private Instant nextScheduledRun;

    /**
     * Research executions belonging to this market research.
     * Cascade ALL operations, orphan removal enabled.
     */
    @OneToMany(mappedBy = "marketResearch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ResearchExecutionJpaEntity> executions = new ArrayList<>();

    /**
     * Owner of this market research (references users table).
     */
    @Column(name = "owner_id")
    private UUID ownerId;

    /**
     * Whether this research is paused due to an external reason (e.g., budget depleted).
     */
    @Column(name = "paused_for_external_reason", nullable = false)
    private boolean pausedForExternalReason = false;

    /**
     * The reason for the external pause.
     */
    @Column(name = "external_pause_reason", length = 100)
    private String externalPauseReason;

    /**
     * Convert domain entity to JPA entity.
     */
    public static MarketResearchJpaEntity fromDomain(MarketResearch domain) {
        var jpaEntity = new MarketResearchJpaEntity();

        jpaEntity.id = domain.getId();
        jpaEntity.name = domain.getName();
        jpaEntity.description = domain.getMarkdownInstructions();

        // Convert Set<Symbol> to comma-separated string (ticker only)
        jpaEntity.targetAssets = domain.getTargetAssets().stream()
            .map(Symbol::ticker)
            .sorted() // Sort for consistent ordering
            .collect(Collectors.joining(","));

        // Convert ResearchInterval to amount + unit
        jpaEntity.intervalAmount = domain.getInterval().amount();
        jpaEntity.intervalUnit = domain.getInterval().unit().name();

        // Convert ResearchStatus enum to string
        jpaEntity.status = domain.getStatus().name();

        // Handle backtest configuration
        jpaEntity.autoBacktest = domain.isEnableAutoBacktest();
        if (domain.getBacktestConfig() != null) {
            BacktestConfiguration config = domain.getBacktestConfig();
            jpaEntity.backtestLengthSeconds = config.backtestLength().getSeconds();
            jpaEntity.backtestPeriodStart = config.periodStart();
            jpaEntity.backtestPeriodEnd = config.periodEnd();
        }

        // Timestamps
        jpaEntity.createdAt = domain.getCreatedAt();
        jpaEntity.lastExecutedAt = domain.getLastExecutedAt();
        jpaEntity.nextScheduledRun = domain.getNextScheduledRun();

        // Ownership
        jpaEntity.ownerId = domain.getOwnerId() != null ? domain.getOwnerId().value() : null;

        // External pause
        jpaEntity.pausedForExternalReason = domain.isPausedForExternalReason();
        jpaEntity.externalPauseReason = domain.getExternalPauseReason();

        return jpaEntity;
    }

    /**
     * Convert JPA entity to domain entity.
     */
    public MarketResearch toDomain() {
        // Convert comma-separated string to Set<Symbol> (handle null/empty)
        Set<Symbol> symbols = targetAssets == null || targetAssets.isBlank()
            ? Set.of()
            : Arrays.stream(targetAssets.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> new Symbol(s, InvestmentType.fromTicker(s)))
                .collect(Collectors.toSet());

        // Convert amount + unit to ResearchInterval
        ResearchInterval interval = new ResearchInterval(
            intervalAmount,
            ResearchInterval.Unit.valueOf(intervalUnit)
        );

        // Convert backtest fields to BacktestConfiguration (or null)
        BacktestConfiguration backtestConfig = null;
        if (backtestLengthSeconds != null) {
            backtestConfig = new BacktestConfiguration(
                Duration.ofSeconds(backtestLengthSeconds),
                backtestPeriodStart,
                backtestPeriodEnd
            );
        }

        // Use reflection to reconstruct domain entity with all fields
        // (MarketResearch constructor creates default values, we need to override them)
        return new MarketResearchBuilder()
            .id(id)
            .name(name)
            .markdownInstructions(description)
            .targetAssets(symbols)
            .interval(interval)
            .status(ResearchStatus.valueOf(status))
            .enableAutoBacktest(autoBacktest)
            .backtestConfig(backtestConfig)
            .createdAt(createdAt)
            .lastExecutedAt(lastExecutedAt)
            .nextScheduledRun(nextScheduledRun)
            .ownerId(ownerId != null ? new UserId(ownerId) : null)
            .pausedForExternalReason(pausedForExternalReason)
            .externalPauseReason(externalPauseReason)
            .build();
    }

    /**
     * Builder to reconstruct domain entity with all fields.
     * Necessary because MarketResearch has immutable fields set in constructor.
     */
    private static class MarketResearchBuilder {
        private UUID id;
        private String name;
        private String markdownInstructions;
        private Set<Symbol> targetAssets;
        private ResearchInterval interval;
        private ResearchStatus status;
        private boolean enableAutoBacktest;
        private BacktestConfiguration backtestConfig;
        private Instant createdAt;
        private Instant lastExecutedAt;
        private Instant nextScheduledRun;
        private UserId ownerId;
        private boolean pausedForExternalReason;
        private String externalPauseReason;

        MarketResearchBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        MarketResearchBuilder name(String name) {
            this.name = name;
            return this;
        }

        MarketResearchBuilder markdownInstructions(String markdownInstructions) {
            this.markdownInstructions = markdownInstructions;
            return this;
        }

        MarketResearchBuilder targetAssets(Set<Symbol> targetAssets) {
            this.targetAssets = targetAssets;
            return this;
        }

        MarketResearchBuilder interval(ResearchInterval interval) {
            this.interval = interval;
            return this;
        }

        MarketResearchBuilder status(ResearchStatus status) {
            this.status = status;
            return this;
        }

        MarketResearchBuilder enableAutoBacktest(boolean enableAutoBacktest) {
            this.enableAutoBacktest = enableAutoBacktest;
            return this;
        }

        MarketResearchBuilder backtestConfig(BacktestConfiguration backtestConfig) {
            this.backtestConfig = backtestConfig;
            return this;
        }

        MarketResearchBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        MarketResearchBuilder lastExecutedAt(Instant lastExecutedAt) {
            this.lastExecutedAt = lastExecutedAt;
            return this;
        }

        MarketResearchBuilder nextScheduledRun(Instant nextScheduledRun) {
            this.nextScheduledRun = nextScheduledRun;
            return this;
        }

        MarketResearchBuilder ownerId(UserId ownerId) {
            this.ownerId = ownerId;
            return this;
        }

        MarketResearchBuilder pausedForExternalReason(boolean pausedForExternalReason) {
            this.pausedForExternalReason = pausedForExternalReason;
            return this;
        }

        MarketResearchBuilder externalPauseReason(String externalPauseReason) {
            this.externalPauseReason = externalPauseReason;
            return this;
        }

        MarketResearch build() {
            try {
                // For legacy data without ownerId, use system user
                UserId effectiveOwnerId = ownerId != null
                    ? ownerId
                    : new UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

                // Create domain entity using constructor
                var domain = new MarketResearch(
                    name,
                    markdownInstructions,
                    targetAssets,
                    interval,
                    enableAutoBacktest,
                    backtestConfig,
                    effectiveOwnerId
                );

                // Use reflection to set immutable fields that differ from constructor defaults
                var idField = MarketResearch.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(domain, id);

                var statusField = MarketResearch.class.getDeclaredField("status");
                statusField.setAccessible(true);
                statusField.set(domain, status);

                var createdAtField = MarketResearch.class.getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(domain, createdAt);

                if (lastExecutedAt != null) {
                    var lastExecutedAtField = MarketResearch.class.getDeclaredField("lastExecutedAt");
                    lastExecutedAtField.setAccessible(true);
                    lastExecutedAtField.set(domain, lastExecutedAt);
                }

                var nextScheduledRunField = MarketResearch.class.getDeclaredField("nextScheduledRun");
                nextScheduledRunField.setAccessible(true);
                nextScheduledRunField.set(domain, nextScheduledRun);

                // Set external pause fields
                if (pausedForExternalReason) {
                    domain.pauseForExternalReason(externalPauseReason);
                }

                return domain;
            } catch (Exception e) {
                throw new RuntimeException("Failed to reconstruct MarketResearch domain entity", e);
            }
        }
    }

    // Getters and setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTargetAssets() {
        return targetAssets;
    }

    public void setTargetAssets(String targetAssets) {
        this.targetAssets = targetAssets;
    }

    public Integer getIntervalAmount() {
        return intervalAmount;
    }

    public void setIntervalAmount(Integer intervalAmount) {
        this.intervalAmount = intervalAmount;
    }

    public String getIntervalUnit() {
        return intervalUnit;
    }

    public void setIntervalUnit(String intervalUnit) {
        this.intervalUnit = intervalUnit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isAutoBacktest() {
        return autoBacktest;
    }

    public void setAutoBacktest(boolean autoBacktest) {
        this.autoBacktest = autoBacktest;
    }

    public Long getBacktestLengthSeconds() {
        return backtestLengthSeconds;
    }

    public void setBacktestLengthSeconds(Long backtestLengthSeconds) {
        this.backtestLengthSeconds = backtestLengthSeconds;
    }

    public Instant getBacktestPeriodStart() {
        return backtestPeriodStart;
    }

    public void setBacktestPeriodStart(Instant backtestPeriodStart) {
        this.backtestPeriodStart = backtestPeriodStart;
    }

    public Instant getBacktestPeriodEnd() {
        return backtestPeriodEnd;
    }

    public void setBacktestPeriodEnd(Instant backtestPeriodEnd) {
        this.backtestPeriodEnd = backtestPeriodEnd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastExecutedAt() {
        return lastExecutedAt;
    }

    public void setLastExecutedAt(Instant lastExecutedAt) {
        this.lastExecutedAt = lastExecutedAt;
    }

    public Instant getNextScheduledRun() {
        return nextScheduledRun;
    }

    public void setNextScheduledRun(Instant nextScheduledRun) {
        this.nextScheduledRun = nextScheduledRun;
    }

    public List<ResearchExecutionJpaEntity> getExecutions() {
        return executions;
    }

    public void setExecutions(List<ResearchExecutionJpaEntity> executions) {
        this.executions = executions;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public boolean isPausedForExternalReason() {
        return pausedForExternalReason;
    }

    public void setPausedForExternalReason(boolean pausedForExternalReason) {
        this.pausedForExternalReason = pausedForExternalReason;
    }

    public String getExternalPauseReason() {
        return externalPauseReason;
    }

    public void setExternalPauseReason(String externalPauseReason) {
        this.externalPauseReason = externalPauseReason;
    }
}
