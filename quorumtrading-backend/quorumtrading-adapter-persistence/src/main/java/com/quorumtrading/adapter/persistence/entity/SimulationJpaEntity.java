package com.quorumtrading.adapter.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;

/**
 * JPA entity for Simulation persistence.
 *
 * Maps to the simulations table created by Flyway migrations.
 * This is SEPARATE from the domain Simulation to maintain hexagonal architecture boundaries.
 *
 * Uses standard JPA (not Panache) for cross-module compatibility.
 */
@Entity
@Table(name = "simulations")
public class SimulationJpaEntity {

    /**
     * Primary key (UUID).
     */
    @Id
    @Column(name = "id")
    private UUID id;

    /**
     * Simulation name/description.
     */
    @Column(name = "name", length = 200, nullable = false)
    private String name;

    /**
     * Foreign key to orchestrator_strategies table (now UUID).
     */
    @Column(name = "strategy_id")
    private UUID strategyId;

    /**
     * Start date/time for the simulation.
     */
    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    /**
     * End date/time for the simulation.
     * NULL for LIVE/ongoing simulations (no scheduled end date) — see migration V31.
     */
    @Column(name = "end_date")
    private Instant endDate;

    /**
     * Initial portfolio state (JSONB).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "initial_portfolio", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> initialPortfolio;

    /**
     * Simulation configuration (JSONB).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> configuration;

    /**
     * Current status of the simulation.
     */
    @Column(name = "status", length = 20)
    private String status;

    /**
     * Indicates whether the simulation execution is paused.
     * Simulations start paused by default.
     */
    @Column(name = "paused", nullable = false)
    private boolean paused = true;

    /**
     * Simulation progress percentage (0-100).
     * Tracked as actual evaluations completed vs total.
     */
    @Column(name = "progress", nullable = false)
    private int progress = 0;

    /**
     * Creation timestamp (auto-set by @PrePersist).
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Start timestamp (set when simulation begins).
     */
    @Column(name = "started_at")
    private Instant startedAt;

    /**
     * Completion timestamp (set when simulation ends).
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Why the run failed. NULL unless status = FAILED. Kept so the API can answer "why" —
     * previously the cause reached the server log only.
     */
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    /**
     * Optional foreign key to benchmarks table.
     * NULL for standalone simulations (manual backtests).
     * Non-NULL for wizard-created simulations competing in a benchmark.
     */
    @Column(name = "benchmark_id")
    private UUID benchmarkId;

    /**
     * Optional foreign key to challenges table.
     * NULL for simulations not in a challenge.
     */
    @Column(name = "challenge_id")
    private UUID challengeId;

    /**
     * Optional foreign key to broker_accounts table.
     * When set, this simulation uses the specified broker account for trading.
     * When NULL, falls back to legacy global broker routing.
     */
    @Column(name = "broker_account_id")
    private UUID brokerAccountId;

    /**
     * Reserved capital for this simulation (amount).
     * Used with broker accounts to limit trading capital.
     */
    @Column(name = "trading_limit_amount")
    private java.math.BigDecimal tradingLimitAmount;

    /**
     * Reserved capital for this simulation (currency).
     */
    @Column(name = "trading_limit_currency", length = 3)
    private String tradingLimitCurrency;

    /**
     * Whether this simulation is paused due to an external reason (e.g., budget depleted).
     */
    @Column(name = "paused_for_external_reason", nullable = false)
    private boolean pausedForExternalReason = false;

    /**
     * The reason for the external pause.
     */
    @Column(name = "external_pause_reason", length = 100)
    private String externalPauseReason;

    // ========== Distributed Locking ==========

    /**
     * Optimistic locking version for concurrent modification detection.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /**
     * Instance ID that currently holds the execution lock.
     * NULL when unlocked.
     */
    @Column(name = "locked_by")
    private String lockedBy;

    /**
     * Timestamp when the lock was acquired.
     * Used for stale lock detection.
     */
    @Column(name = "locked_at")
    private Instant lockedAt;

    // ========== Backfill Tracking ==========

    /**
     * Reference to currently running backfill job.
     * NULL when no backfill is pending.
     */
    @Column(name = "backfill_job_id")
    private UUID backfillJobId;

    /**
     * Consecutive skipped evaluations (resets on successful evaluation).
     * Used by circuit breaker to detect data gaps during execution.
     */
    @Column(name = "skipped_evaluation_count", nullable = false)
    private int skippedEvaluationCount = 0;

    /**
     * Total skipped evaluations (for percentage calculation).
     */
    @Column(name = "total_skipped_evaluation_count", nullable = false)
    private int totalSkippedEvaluationCount = 0;

    /**
     * Total evaluations attempted (for percentage calculation).
     */
    @Column(name = "total_evaluation_count", nullable = false)
    private int totalEvaluationCount = 0;

    // ========== Archived LLM Cost (restart-from-decision) ==========

    @Column(name = "archived_llm_cost_usd", nullable = false)
    private BigDecimal archivedLlmCostUsd = BigDecimal.ZERO;

    @Column(name = "archived_llm_prompt_tokens", nullable = false)
    private int archivedLlmPromptTokens = 0;

    @Column(name = "archived_llm_completion_tokens", nullable = false)
    private int archivedLlmCompletionTokens = 0;

    @Column(name = "archived_llm_total_tokens", nullable = false)
    private int archivedLlmTotalTokens = 0;

    @Column(name = "archived_llm_decision_count", nullable = false)
    private int archivedLlmDecisionCount = 0;

    // ========== Notification controls (V114) ==========

    /**
     * Denormalised owner — FK to users.id. Used by notification dispatch to avoid
     * a broker_accounts join on every fire. NOT NULL in DB; backfilled in V114.
     */
    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    /**
     * Per-simulation opt-in for SIMULATION_DECISION notifications. Default false
     * — user explicitly enables this. HOLDs never fire regardless.
     */
    @Column(name = "notify_decisions", nullable = false)
    private boolean notifyDecisions = false;

    /**
     * Set default values before persisting new entity.
     */
    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (initialPortfolio == null) {
            initialPortfolio = Map.of();
        }
        if (configuration == null) {
            configuration = Map.of();
        }
        if (status == null) {
            status = "PENDING";
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

    public UUID getStrategyId() {
        return strategyId;
    }

    public void setStrategyId(UUID strategyId) {
        this.strategyId = strategyId;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }

    public Map<String, Object> getInitialPortfolio() {
        return initialPortfolio;
    }

    public void setInitialPortfolio(Map<String, Object> initialPortfolio) {
        this.initialPortfolio = initialPortfolio;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public UUID getBenchmarkId() {
        return benchmarkId;
    }

    public void setBenchmarkId(UUID benchmarkId) {
        this.benchmarkId = benchmarkId;
    }

    public UUID getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(UUID challengeId) {
        this.challengeId = challengeId;
    }

    public UUID getBrokerAccountId() {
        return brokerAccountId;
    }

    public void setBrokerAccountId(UUID brokerAccountId) {
        this.brokerAccountId = brokerAccountId;
    }

    public java.math.BigDecimal getTradingLimitAmount() {
        return tradingLimitAmount;
    }

    public void setTradingLimitAmount(java.math.BigDecimal tradingLimitAmount) {
        this.tradingLimitAmount = tradingLimitAmount;
    }

    public String getTradingLimitCurrency() {
        return tradingLimitCurrency;
    }

    public void setTradingLimitCurrency(String tradingLimitCurrency) {
        this.tradingLimitCurrency = tradingLimitCurrency;
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

    // ========== Backfill Tracking Getters/Setters ==========

    public UUID getBackfillJobId() {
        return backfillJobId;
    }

    public void setBackfillJobId(UUID backfillJobId) {
        this.backfillJobId = backfillJobId;
    }

    public int getSkippedEvaluationCount() {
        return skippedEvaluationCount;
    }

    public void setSkippedEvaluationCount(int skippedEvaluationCount) {
        this.skippedEvaluationCount = skippedEvaluationCount;
    }

    public int getTotalSkippedEvaluationCount() {
        return totalSkippedEvaluationCount;
    }

    public void setTotalSkippedEvaluationCount(int totalSkippedEvaluationCount) {
        this.totalSkippedEvaluationCount = totalSkippedEvaluationCount;
    }

    public int getTotalEvaluationCount() {
        return totalEvaluationCount;
    }

    public void setTotalEvaluationCount(int totalEvaluationCount) {
        this.totalEvaluationCount = totalEvaluationCount;
    }

    // ========== Distributed Locking Getters/Setters ==========

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    // ========== Archived LLM Cost Getters/Setters ==========

    public BigDecimal getArchivedLlmCostUsd() {
        return archivedLlmCostUsd;
    }

    public void setArchivedLlmCostUsd(BigDecimal archivedLlmCostUsd) {
        this.archivedLlmCostUsd = archivedLlmCostUsd;
    }

    public int getArchivedLlmPromptTokens() {
        return archivedLlmPromptTokens;
    }

    public void setArchivedLlmPromptTokens(int archivedLlmPromptTokens) {
        this.archivedLlmPromptTokens = archivedLlmPromptTokens;
    }

    public int getArchivedLlmCompletionTokens() {
        return archivedLlmCompletionTokens;
    }

    public void setArchivedLlmCompletionTokens(int archivedLlmCompletionTokens) {
        this.archivedLlmCompletionTokens = archivedLlmCompletionTokens;
    }

    public int getArchivedLlmTotalTokens() {
        return archivedLlmTotalTokens;
    }

    public void setArchivedLlmTotalTokens(int archivedLlmTotalTokens) {
        this.archivedLlmTotalTokens = archivedLlmTotalTokens;
    }

    public int getArchivedLlmDecisionCount() {
        return archivedLlmDecisionCount;
    }

    public void setArchivedLlmDecisionCount(int archivedLlmDecisionCount) {
        this.archivedLlmDecisionCount = archivedLlmDecisionCount;
    }

    // ========== Notification controls (V114) ==========

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public boolean isNotifyDecisions() {
        return notifyDecisions;
    }

    public void setNotifyDecisions(boolean notifyDecisions) {
        this.notifyDecisions = notifyDecisions;
    }
}
