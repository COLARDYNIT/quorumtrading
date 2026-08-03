package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.ChallengeId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain entity representing a trading simulation (backtest or live).
 * A simulation runs a strategy over a time period to evaluate performance.
 *
 * <p><b>Aggregate Root</b> - Controls access to child entities (decisions, snapshots).</p>
 *
 * <p><b>Aggregate Boundary:</b></p>
 * <ul>
 *   <li><b>Root:</b> Simulation</li>
 *   <li><b>Child Entities:</b> PortfolioSnapshot, LLMDecision, ConsensusDecision</li>
 *   <li><b>External References:</b> Strategy (by StrategyId), MarketEvent (shared data)</li>
 * </ul>
 *
 * <p><b>Invariants Enforced:</b></p>
 * <ul>
 *   <li>Simulation cannot complete before its scheduled end date</li>
 *   <li>Only PENDING simulations can be started</li>
 *   <li>Only SIMULATING/LIVE simulations can be paused</li>
 *   <li>COMPLETED/FAILED simulations are immutable (terminal states)</li>
 *   <li>Progress must be between 0-100</li>
 *   <li>State transitions must follow valid state machine paths</li>
 *   <li><b>LIVE simulations require startDate >= today at 00:00:00 (cannot start in the past)</b></li>
 * </ul>
 *
 * <p><b>BACKTEST vs LIVE Mode (based on endDate):</b></p>
 * <ul>
 *   <li><b>BACKTEST:</b> endDate <= now → Status SIMULATING (historical analysis)</li>
 *   <li><b>LIVE:</b> endDate > now OR endDate == null → Status LIVE (real-time trading)</li>
 * </ul>
 *
 * <p><b>State Machine:</b></p>
 * <pre>
 * PENDING → WAITING_FOR_DATA → PENDING → SIMULATING (endDate <= now) → COMPLETED
 *    │           │               │      → LIVE (endDate > now or null) → COMPLETED
 *    │           │               │              ↓                        ↓
 *    │           ▼               │           FAILED                   FAILED
 *    │        FAILED             │
 *    │    (backfill failed)      │
 *    │                           │
 *    └───────────────────────────┘ (no backfill needed - data exists)
 *
 * Pause/Resume: SIMULATING ⇄ paused, LIVE ⇄ paused
 * Transition: SIMULATING → LIVE (when backtest catches up to real-time)
 * Circuit Breaker: During execution, if too many evaluations skip (data gaps),
 *                  simulation pauses with reason "DATA_GAP_DETECTED"
 * </pre>
 *
 * <p><b>Lifecycle States:</b></p>
 * <ul>
 *   <li><b>PENDING:</b> Created but not yet started</li>
 *   <li><b>WAITING_FOR_DATA:</b> Waiting for market data backfill to complete</li>
 *   <li><b>SIMULATING:</b> Running historical backtest (endDate in the past)</li>
 *   <li><b>LIVE:</b> Real-time trading mode (endDate in future or null)</li>
 *   <li><b>COMPLETED:</b> Successfully finished (terminal state)</li>
 *   <li><b>FAILED:</b> Encountered error (terminal state)</li>
 * </ul>
 *
 * @see SimulationStatus
 * @see PortfolioSnapshot
 * @see LLMDecision
 * @see ConsensusDecision
 */
public class Simulation implements AggregateRoot {

    private SimulationId simulationId;
    private String name;
    private StrategyId strategyId;
    private Instant startDate;
    private Instant endDate;
    private Money initialCash;
    private BenchmarkId benchmarkId;  // Optional: null for simulations not in a benchmark
    private ChallengeId challengeId;  // Optional: null for simulations not in a challenge
    private BrokerAccountId brokerAccountId;  // Nullable during migration - will replace global broker routing
    private Money tradingLimit;  // Nullable during migration - reserved capital for this simulation
    private SimulationStatus status;
    private int progress;
    private boolean paused;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
    /** Why the run failed. Null unless {@code status == FAILED}. */
    private String failureReason;

    // Distributed locking for horizontal scaling
    private long version;
    private String lockedBy;
    private Instant lockedAt;

    // External pause state (e.g., budget depleted)
    private boolean pausedForExternalReason;
    private String externalPauseReason;

    // Backfill tracking for robust backtest execution
    private java.util.UUID backfillJobId;           // Currently linked backfill job (null when no backfill pending)
    private int skippedEvaluationCount;             // Consecutive skipped evaluations (for circuit breaker)
    private int totalSkippedEvaluationCount;        // Total skipped evaluations (for percentage calculation)
    private int totalEvaluationCount;               // Total evaluations attempted (for percentage calculation)

    // Archived LLM cost from deleted decisions (restart-from-decision feature)
    private BigDecimal archivedLLMCostUsd = BigDecimal.ZERO;
    private int archivedLLMPromptTokens;
    private int archivedLLMCompletionTokens;
    private int archivedLLMTotalTokens;
    private int archivedLLMDecisionCount;

    // Notification controls (V114)
    private UserId ownerUserId;            // Denormalised owner — drives notification dispatch routing
    private boolean notifyDecisions;       // Per-sim opt-in for SIMULATION_DECISION fires; HOLDs never fire either way

    /** No-arg constructor for reconstitution only. */
    private Simulation() {}

    /**
     * Validates that a backtest does not extend into the future.
     * A backtest is identified by startDate being before today (yesterday or earlier).
     * For such backtests, endDate must not be after end of today.
     *
     * @throws IllegalArgumentException if startDate is in the past and endDate is in the future
     */
    private static void validateBacktestDates(Instant startDate, Instant endDate) {
        if (BacktestWindow.extendsIntoTheFuture(
                startDate, endDate, Instant.now(), java.time.ZoneOffset.UTC)) {
            throw new IllegalArgumentException(BacktestWindow.rejectionMessage(startDate, endDate));
        }
    }

    public Simulation(
        SimulationId simulationId,
        String name,
        StrategyId strategyId,
        Instant startDate,
        Instant endDate,
        Money initialCash,
        BenchmarkId benchmarkId
    ) {
        validateBacktestDates(startDate, endDate);
        this.simulationId = simulationId;
        this.name = name;
        this.strategyId = strategyId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.initialCash = initialCash;
        this.benchmarkId = benchmarkId;  // Nullable for backward compatibility
        this.status = SimulationStatus.PENDING;
        this.progress = 0;
        this.paused = true; // Simulations start paused by default
        this.createdAt = Instant.now();  // Set creation timestamp
    }

    /**
     * Constructor without benchmark (for backward compatibility)
     */
    public Simulation(
        SimulationId simulationId,
        String name,
        StrategyId strategyId,
        Instant startDate,
        Instant endDate,
        Money initialCash
    ) {
        this(simulationId, name, strategyId, startDate, endDate, initialCash, null);
    }

    /**
     * Constructor with broker account support.
     * Used for new simulations that specify a broker account and trading limit.
     *
     * @param simulationId unique identifier for this simulation
     * @param name display name for the simulation
     * @param strategyId reference to the strategy being tested
     * @param startDate simulation start date
     * @param endDate simulation end date (null for ongoing LIVE simulations)
     * @param initialCash initial cash amount (kept for backward compatibility during migration)
     * @param benchmarkId optional reference to a benchmark
     * @param brokerAccountId optional broker account (nullable during migration)
     * @param tradingLimit optional trading limit (nullable during migration)
     */
    public Simulation(
        SimulationId simulationId,
        String name,
        StrategyId strategyId,
        Instant startDate,
        Instant endDate,
        Money initialCash,
        BenchmarkId benchmarkId,
        BrokerAccountId brokerAccountId,
        Money tradingLimit
    ) {
        this(simulationId, name, strategyId, startDate, endDate, initialCash, benchmarkId);
        this.brokerAccountId = brokerAccountId;
        this.tradingLimit = tradingLimit;
    }

    /**
     * Reconstitute a Simulation from persistence. Skips date validation since the entity
     * was already validated at creation time and may predate current rules.
     *
     * <p>Restores default lifecycle state (PENDING, paused, createdAt=now). For restoring
     * the full persisted state (status, timestamps, progress, ...) use {@link #reconstitution()}.</p>
     */
    public static Simulation reconstitute(
        SimulationId simulationId,
        String name,
        StrategyId strategyId,
        Instant startDate,
        Instant endDate,
        Money initialCash,
        BenchmarkId benchmarkId,
        BrokerAccountId brokerAccountId,
        Money tradingLimit
    ) {
        return reconstitution()
            .simulationId(simulationId)
            .name(name)
            .strategyId(strategyId)
            .startDate(startDate)
            .endDate(endDate)
            .initialCash(initialCash)
            .benchmarkId(benchmarkId)
            .brokerAccountId(brokerAccountId)
            .tradingLimit(tradingLimit)
            .status(SimulationStatus.PENDING)
            .progress(0)
            .paused(true)
            .createdAt(Instant.now())
            .build();
    }

    /**
     * Entry point for full reconstitution from persistence.
     *
     * <p>Restores already-validated persisted state <b>directly</b>, with NO state-transition
     * validation — reconstitution is not a transition. In particular, a force-completed LIVE
     * simulation (status COMPLETED with {@code endDate == null} or in the future) must load
     * back exactly as persisted; running it through {@link #setStatus(SimulationStatus)}
     * would incorrectly re-apply the wall-clock completion rule.</p>
     */
    public static ReconstitutionBuilder reconstitution() {
        return new ReconstitutionBuilder();
    }

    /**
     * Builder that restores a Simulation from persisted state without transition validation.
     * For use by the persistence layer (and tests) only.
     */
    public static final class ReconstitutionBuilder {
        private final Simulation sim = new Simulation();

        private ReconstitutionBuilder() {
            sim.status = SimulationStatus.PENDING;
            sim.createdAt = Instant.now();
        }

        public ReconstitutionBuilder simulationId(SimulationId simulationId) { sim.simulationId = simulationId; return this; }
        public ReconstitutionBuilder name(String name) { sim.name = name; return this; }
        public ReconstitutionBuilder strategyId(StrategyId strategyId) { sim.strategyId = strategyId; return this; }
        public ReconstitutionBuilder startDate(Instant startDate) { sim.startDate = startDate; return this; }
        public ReconstitutionBuilder endDate(Instant endDate) { sim.endDate = endDate; return this; }
        public ReconstitutionBuilder initialCash(Money initialCash) { sim.initialCash = initialCash; return this; }
        public ReconstitutionBuilder benchmarkId(BenchmarkId benchmarkId) { sim.benchmarkId = benchmarkId; return this; }
        public ReconstitutionBuilder challengeId(ChallengeId challengeId) { sim.challengeId = challengeId; return this; }
        public ReconstitutionBuilder brokerAccountId(BrokerAccountId brokerAccountId) { sim.brokerAccountId = brokerAccountId; return this; }
        public ReconstitutionBuilder tradingLimit(Money tradingLimit) { sim.tradingLimit = tradingLimit; return this; }

        /** Restores the persisted status directly — no transition validation. Null keeps the PENDING default. */
        public ReconstitutionBuilder status(SimulationStatus status) {
            if (status != null) {
                sim.status = status;
            }
            return this;
        }

        public ReconstitutionBuilder progress(int progress) { sim.progress = Math.min(100, Math.max(0, progress)); return this; }
        public ReconstitutionBuilder paused(boolean paused) { sim.paused = paused; return this; }
        public ReconstitutionBuilder startedAt(Instant startedAt) { sim.startedAt = startedAt; return this; }
        public ReconstitutionBuilder completedAt(Instant completedAt) { sim.completedAt = completedAt; return this; }
        public ReconstitutionBuilder failureReason(String failureReason) { sim.failureReason = failureReason; return this; }

        /** Restores the persisted creation timestamp. Null keeps the now() default. */
        public ReconstitutionBuilder createdAt(Instant createdAt) {
            if (createdAt != null) {
                sim.createdAt = createdAt;
            }
            return this;
        }

        public ReconstitutionBuilder version(long version) { sim.version = version; return this; }
        public ReconstitutionBuilder lockedBy(String lockedBy) { sim.lockedBy = lockedBy; return this; }
        public ReconstitutionBuilder lockedAt(Instant lockedAt) { sim.lockedAt = lockedAt; return this; }

        public ReconstitutionBuilder externalPause(boolean pausedForExternalReason, String externalPauseReason) {
            sim.pausedForExternalReason = pausedForExternalReason;
            sim.externalPauseReason = pausedForExternalReason ? externalPauseReason : null;
            return this;
        }

        public ReconstitutionBuilder backfillJobId(java.util.UUID backfillJobId) { sim.backfillJobId = backfillJobId; return this; }
        public ReconstitutionBuilder skippedEvaluationCount(int count) { sim.skippedEvaluationCount = count; return this; }
        public ReconstitutionBuilder totalSkippedEvaluationCount(int count) { sim.totalSkippedEvaluationCount = count; return this; }
        public ReconstitutionBuilder totalEvaluationCount(int count) { sim.totalEvaluationCount = count; return this; }

        /** Null restores the ZERO default (field invariant: never null). */
        public ReconstitutionBuilder archivedLLMCostUsd(BigDecimal archivedLLMCostUsd) {
            sim.archivedLLMCostUsd = archivedLLMCostUsd != null ? archivedLLMCostUsd : BigDecimal.ZERO;
            return this;
        }

        public ReconstitutionBuilder archivedLLMPromptTokens(int tokens) { sim.archivedLLMPromptTokens = tokens; return this; }
        public ReconstitutionBuilder archivedLLMCompletionTokens(int tokens) { sim.archivedLLMCompletionTokens = tokens; return this; }
        public ReconstitutionBuilder archivedLLMTotalTokens(int tokens) { sim.archivedLLMTotalTokens = tokens; return this; }
        public ReconstitutionBuilder archivedLLMDecisionCount(int count) { sim.archivedLLMDecisionCount = count; return this; }

        public ReconstitutionBuilder ownerUserId(UserId ownerUserId) { sim.ownerUserId = ownerUserId; return this; }
        public ReconstitutionBuilder notifyDecisions(boolean notifyDecisions) { sim.notifyDecisions = notifyDecisions; return this; }

        public Simulation build() {
            return sim;
        }
    }

    // Getters
    public SimulationId getSimulationId() { return simulationId; }
    public String getName() { return name; }
    public StrategyId getStrategyId() { return strategyId; }
    public Instant getStartDate() { return startDate; }
    public Instant getEndDate() { return endDate; }
    public Money getInitialCash() { return initialCash; }
    public BenchmarkId getBenchmarkId() { return benchmarkId; }
    public ChallengeId getChallengeId() { return challengeId; }
    public BrokerAccountId getBrokerAccountId() { return brokerAccountId; }
    public Money getTradingLimit() { return tradingLimit; }
    public SimulationStatus getStatus() { return status; }
    public int getProgress() { return progress; }
    public boolean isPaused() { return paused; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }

    // Distributed locking getters
    public long getVersion() { return version; }
    public String getLockedBy() { return lockedBy; }
    public Instant getLockedAt() { return lockedAt; }

    // Backfill tracking getters
    public java.util.UUID getBackfillJobId() { return backfillJobId; }
    public int getSkippedEvaluationCount() { return skippedEvaluationCount; }
    public int getTotalSkippedEvaluationCount() { return totalSkippedEvaluationCount; }
    public int getTotalEvaluationCount() { return totalEvaluationCount; }

    // Archived LLM cost getters
    public BigDecimal getArchivedLLMCostUsd() { return archivedLLMCostUsd; }
    public int getArchivedLLMPromptTokens() { return archivedLLMPromptTokens; }
    public int getArchivedLLMCompletionTokens() { return archivedLLMCompletionTokens; }
    public int getArchivedLLMTotalTokens() { return archivedLLMTotalTokens; }
    public int getArchivedLLMDecisionCount() { return archivedLLMDecisionCount; }

    // Notification controls (V114)
    public UserId getOwnerUserId() { return ownerUserId; }
    public boolean isNotifyDecisions() { return notifyDecisions; }

    /**
     * Assign the owner of this simulation. Required before persistence — the
     * database enforces NOT NULL on {@code owner_user_id}. Services creating a
     * new simulation call this immediately after construction; the persistence
     * mapper calls it on reconstitution.
     */
    public void setOwnerUserId(UserId ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    /** Enable per-simulation BUY/SELL decision notifications. */
    public void enableDecisionNotifications() { this.notifyDecisions = true; }

    /** Disable per-simulation BUY/SELL decision notifications. */
    public void disableDecisionNotifications() { this.notifyDecisions = false; }

    /** Setter for JPA mapping only — services should use the enable/disable methods. */
    public void setNotifyDecisions(boolean notifyDecisions) { this.notifyDecisions = notifyDecisions; }

    // ========== Distributed Locking ==========

    /**
     * Acquire a lock on this simulation for a specific instance.
     *
     * @param instanceId the ECS task/instance identifier
     */
    public void lock(String instanceId) {
        this.lockedBy = instanceId;
        this.lockedAt = Instant.now();
    }

    /**
     * Release the lock on this simulation.
     */
    public void unlock() {
        this.lockedBy = null;
        this.lockedAt = null;
    }

    /**
     * Check if this simulation is locked by a specific instance.
     *
     * @param instanceId the instance to check
     * @return true if locked by the specified instance
     */
    public boolean isLockedBy(String instanceId) {
        return instanceId != null && instanceId.equals(this.lockedBy);
    }

    /**
     * Check if this simulation is currently locked.
     *
     * @return true if locked
     */
    public boolean isLocked() {
        return this.lockedBy != null;
    }

    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setVersion(long version) {
        this.version = version;
    }

    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }

    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    // Setters for mutable state (for persistence layer and JPA only)
    // DEPRECATED: Use domain methods (start(), complete(), fail(), transitionToLive(), pause(), resume()) instead
    // These are kept public only to support JPA/Hibernate mapping and persistence adapter reconstruction
    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setStatus(SimulationStatus status) {
        // Domain validation: Cannot mark simulation as COMPLETED before its scheduled end date.
        // endDate == null means a LIVE simulation without a scheduled end — force-completing it
        // (see forceComplete()) is legitimate, so the wall-clock rule does not apply.
        if (status == SimulationStatus.COMPLETED && this.endDate != null) {
            Instant now = Instant.now();
            if (now.isBefore(this.endDate)) {
                throw new IllegalStateException(
                    String.format("Cannot mark simulation %s as COMPLETED before end date %s (current time: %s)",
                        this.simulationId.value(), this.endDate, now)
                );
            }
        }
        this.status = status;
    }

    public void setProgress(int progress) {
        this.progress = Math.min(100, Math.max(0, progress));
    }

    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Sets the broker account ID.
     * For persistence layer use only during migration period.
     *
     * @deprecated Use constructor with brokerAccountId parameter instead
     */
    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setBrokerAccountId(BrokerAccountId brokerAccountId) {
        this.brokerAccountId = brokerAccountId;
    }

    /**
     * Assigns this simulation to a benchmark for comparison grouping.
     * Throws if already assigned to a different benchmark.
     *
     * @param benchmarkId The benchmark to assign to (must not be null)
     * @throws IllegalArgumentException if benchmarkId is null
     * @throws IllegalStateException if already assigned to a different benchmark
     */
    public void assignToBenchmark(BenchmarkId benchmarkId) {
        if (benchmarkId == null) {
            throw new IllegalArgumentException("BenchmarkId cannot be null");
        }
        if (this.benchmarkId != null && !this.benchmarkId.equals(benchmarkId)) {
            throw new IllegalStateException(
                String.format("Simulation %s is already assigned to benchmark %s, cannot reassign to %s",
                    this.simulationId.value(), this.benchmarkId.value(), benchmarkId.value()));
        }
        this.benchmarkId = benchmarkId;
    }

    /**
     * Removes this simulation from its current benchmark.
     * After unassignment, the simulation can be assigned to a different benchmark.
     *
     * @throws IllegalStateException if the simulation is not assigned to any benchmark
     */
    public void unassignFromBenchmark() {
        if (this.benchmarkId == null) {
            throw new IllegalStateException(
                String.format("Simulation %s is not assigned to any benchmark", this.simulationId.value()));
        }
        this.benchmarkId = null;
    }

    /**
     * Sets the benchmark ID.
     * For persistence layer use only.
     *
     * @deprecated Use {@link #assignToBenchmark(BenchmarkId)} instead
     */
    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setBenchmarkId(BenchmarkId benchmarkId) {
        this.benchmarkId = benchmarkId;
    }

    /**
     * Assigns this simulation to a challenge.
     * A simulation can be reassigned to a different challenge (replace-entry logic).
     *
     * @param challengeId The challenge to assign to (must not be null)
     * @throws IllegalArgumentException if challengeId is null
     */
    public void assignToChallenge(ChallengeId challengeId) {
        if (challengeId == null) {
            throw new IllegalArgumentException("ChallengeId cannot be null");
        }
        this.challengeId = challengeId;
    }

    /**
     * Removes this simulation from its current challenge.
     *
     * @throws IllegalStateException if the simulation is not assigned to any challenge
     */
    public void removeFromChallenge() {
        if (this.challengeId == null) {
            throw new IllegalStateException(
                String.format("Simulation %s is not assigned to any challenge", this.simulationId.value()));
        }
        this.challengeId = null;
    }

    /**
     * Sets the challenge ID.
     * For persistence layer use only.
     *
     * @deprecated Use {@link #assignToChallenge(ChallengeId)} instead
     */
    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setChallengeId(ChallengeId challengeId) {
        this.challengeId = challengeId;
    }

    /**
     * Sets the trading limit.
     * For persistence layer use only during migration period.
     *
     * @deprecated Use constructor with tradingLimit parameter instead
     */
    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setTradingLimit(Money tradingLimit) {
        this.tradingLimit = tradingLimit;
    }

    /**
     * Sets the backfill job ID.
     * For persistence layer use only.
     *
     * @deprecated Use domain methods (waitForData, dataReady) instead
     */
    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setBackfillJobId(java.util.UUID backfillJobId) {
        this.backfillJobId = backfillJobId;
    }

    /**
     * Sets the skipped evaluation count.
     * For persistence layer use only.
     *
     * @deprecated Use domain methods instead
     */
    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setSkippedEvaluationCount(int count) {
        this.skippedEvaluationCount = count;
    }

    /**
     * Sets the total skipped evaluation count.
     * For persistence layer use only.
     *
     * @deprecated Use domain methods instead
     */
    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setTotalSkippedEvaluationCount(int count) {
        this.totalSkippedEvaluationCount = count;
    }

    /**
     * Sets the total evaluation count.
     * For persistence layer use only.
     *
     * @deprecated Use domain methods instead
     */
    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setTotalEvaluationCount(int count) {
        this.totalEvaluationCount = count;
    }

    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setArchivedLLMCostUsd(BigDecimal archivedLLMCostUsd) {
        this.archivedLLMCostUsd = archivedLLMCostUsd;
    }

    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setArchivedLLMPromptTokens(int archivedLLMPromptTokens) {
        this.archivedLLMPromptTokens = archivedLLMPromptTokens;
    }

    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setArchivedLLMCompletionTokens(int archivedLLMCompletionTokens) {
        this.archivedLLMCompletionTokens = archivedLLMCompletionTokens;
    }

    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setArchivedLLMTotalTokens(int archivedLLMTotalTokens) {
        this.archivedLLMTotalTokens = archivedLLMTotalTokens;
    }

    @Deprecated(forRemoval = false, since = "1.0.0")
    public void setArchivedLLMDecisionCount(int archivedLLMDecisionCount) {
        this.archivedLLMDecisionCount = archivedLLMDecisionCount;
    }

    // Business methods

    /**
     * Starts the simulation.
     * Transitions from PENDING to SIMULATING (backtest) or LIVE (real-time).
     *
     * <p><b>Mode Determination Logic:</b></p>
     * <ul>
     *   <li><b>BACKTEST:</b> startDate before today OR endDate <= now → SIMULATING status</li>
     *   <li><b>LIVE:</b> startDate is today or later, AND (endDate > now OR endDate == null) → LIVE status</li>
     * </ul>
     *
     * @throws IllegalStateException if simulation is not in PENDING state
     */
    public void start() {
        if (this.status != SimulationStatus.PENDING) {
            throw new IllegalStateException(
                String.format("Cannot start simulation %s: already started (current status: %s)",
                    this.simulationId.value(), this.status)
            );
        }

        // Determine initial status based on start date and end date. A day starts at 00:00 UTC —
        // the same boundary the budget, rate-limit and DCA-funding days use. Reading the host's
        // zone here made a simulation's classification depend on where the process happened to run.
        Instant now = Instant.now();
        Instant todayMidnight = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS);

        if (this.startDate.isBefore(todayMidnight)) {
            // BACKTEST mode: startDate is before today → replaying historical data
            this.status = SimulationStatus.SIMULATING;
        } else if (this.endDate == null || this.endDate.isAfter(now)) {
            // LIVE mode: startDate is today or later, endDate is null (ongoing) or in the future
            this.status = SimulationStatus.LIVE;
        } else {
            // BACKTEST mode: endDate is in the past (endDate <= now)
            this.status = SimulationStatus.SIMULATING;
        }

        this.startedAt = now;
        this.paused = false;  // Unpause when starting
    }

    /**
     * Transition simulation from SIMULATING to LIVE status.
     * Called by SimulationExecutor when historical backtest catches up to current time.
     *
     * @throws IllegalStateException if simulation is not in SIMULATING state
     */
    public void transitionToLive() {
        if (this.status != SimulationStatus.SIMULATING) {
            throw new IllegalStateException(
                String.format("Cannot transition simulation %s to LIVE: not in SIMULATING state (current status: %s)",
                    this.simulationId.value(), this.status)
            );
        }
        this.status = SimulationStatus.LIVE;
    }

    /**
     * Completes the simulation successfully.
     * Terminal state - no further transitions allowed.
     *
     * @throws IllegalStateException if simulation cannot be completed (end date not reached or invalid state)
     */
    public void complete() {
        if (this.status == SimulationStatus.COMPLETED || this.status == SimulationStatus.FAILED) {
            throw new IllegalStateException(
                String.format("Cannot complete simulation %s: already in terminal state %s",
                    this.simulationId.value(), this.status)
            );
        }

        // Set status directly — the caller (SimulationExecutor) validates that simulation TIME
        // has reached endDate. The deprecated setStatus() wall-clock check is wrong for backtests
        // where simulation time != wall clock time (a backtest can simulate months in minutes).
        this.status = SimulationStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.progress = 100;
        this.paused = false;  // Reset paused flag - completed simulations are not paused
    }

    /**
     * Force-completes the simulation, bypassing the end-date check.
     * Used when a user manually ends a LIVE simulation before its scheduled end date.
     *
     * <p>Progress is deliberately NOT set to 100. A force-ended simulation stopped where it
     * stopped: stamping 100 on a run that covered a third of its period claims the whole
     * period was simulated, and every performance figure derived from it is then read as
     * covering the full window. The status says it ended; the progress says how far it got.</p>
     *
     * @throws IllegalStateException if simulation is already in a terminal state
     */
    public void forceComplete() {
        if (isTerminal()) {
            throw new IllegalStateException(
                String.format("Cannot force-complete simulation %s: already in terminal state %s",
                    this.simulationId.value(), this.status)
            );
        }
        this.status = SimulationStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.paused = false;
    }

    /**
     * Adopts a terminal status observed on a freshly persisted copy of this simulation.
     * Used by the executor when it detects the simulation was completed/failed externally
     * (e.g. force-completed by the user) while it was running.
     *
     * <p>NO transition validation: the persisted state is authoritative and was already
     * validated when it was written. In particular, a force-completed LIVE simulation may
     * have {@code endDate == null} or in the future — re-running the wall-clock completion
     * rule here would incorrectly reject it.</p>
     *
     * @param persistedStatus the terminal status read from persistence
     * @throws IllegalArgumentException if the status is not terminal
     */
    public void adoptTerminalStatus(SimulationStatus persistedStatus) {
        if (persistedStatus != SimulationStatus.COMPLETED && persistedStatus != SimulationStatus.FAILED) {
            throw new IllegalArgumentException(
                String.format("Cannot adopt non-terminal status %s for simulation %s",
                    persistedStatus, this.simulationId.value())
            );
        }
        this.status = persistedStatus;
    }

    /** Longest failure reason we keep; the column is sized to match. */
    public static final int MAX_FAILURE_REASON_LENGTH = 1000;

    /**
     * Marks the simulation as failed, recording why.
     * Terminal state - no further transitions allowed.
     *
     * <p>The reason is part of the aggregate on purpose. It used to exist only in the server log,
     * so every API answered a failed simulation with nothing but {@code status: FAILED} — "no
     * price data for that range", "the LLM provider is down" and "the broker rejected the order"
     * were indistinguishable to anyone driving the platform through the API or MCP, and those
     * three have nothing in common as fixes.</p>
     *
     * @param reason human-readable cause, truncated to {@value #MAX_FAILURE_REASON_LENGTH}
     *               characters; may be null when genuinely unknown
     */
    public void fail(String reason) {
        if (this.status == SimulationStatus.COMPLETED || this.status == SimulationStatus.FAILED) {
            throw new IllegalStateException(
                String.format("Cannot fail simulation %s: already in terminal state %s",
                    this.simulationId.value(), this.status)
            );
        }

        this.status = SimulationStatus.FAILED;
        this.completedAt = Instant.now();
        this.paused = false;  // Reset paused flag - failed simulations are not paused
        this.failureReason = truncateFailureReason(reason);
    }

    private static String truncateFailureReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.strip();
        return trimmed.length() <= MAX_FAILURE_REASON_LENGTH
            ? trimmed
            : trimmed.substring(0, MAX_FAILURE_REASON_LENGTH - 1) + "…";
    }

    /**
     * Why this simulation failed, or null when it has not failed (or the cause was not recorded).
     */
    public String getFailureReason() {
        return failureReason;
    }

    /** Restores a persisted failure reason when rehydrating the aggregate. */
    public void setFailureReason(String failureReason) {
        this.failureReason = truncateFailureReason(failureReason);
    }

    /**
     * Updates simulation progress (0-100).
     *
     * @param progress Progress percentage (automatically clamped to 0-100)
     */
    public void updateProgress(int progress) {
        this.progress = Math.min(100, Math.max(0, progress));
    }

    /**
     * Pauses the simulation.
     * Only allowed for SIMULATING or LIVE simulations.
     *
     * @throws IllegalStateException if simulation is not in a pausable state
     */
    public void pause() {
        if (this.status != SimulationStatus.SIMULATING && this.status != SimulationStatus.LIVE) {
            throw new IllegalStateException(
                String.format("Cannot pause simulation %s: not in SIMULATING or LIVE state (current status: %s)",
                    this.simulationId.value(), this.status)
            );
        }
        this.paused = true;
    }

    /**
     * Resumes a paused simulation.
     * Only allowed for SIMULATING or LIVE simulations.
     *
     * @throws IllegalStateException if simulation is not in a resumable state
     */
    public void resume() {
        if (this.status != SimulationStatus.SIMULATING && this.status != SimulationStatus.LIVE) {
            throw new IllegalStateException(
                String.format("Cannot resume simulation %s: not in SIMULATING or LIVE state (current status: %s)",
                    this.simulationId.value(), this.status)
            );
        }
        this.paused = false;
    }

    /**
     * Sets the paused state directly.
     * Use pause() and resume() for validated state transitions.
     *
     * @deprecated Use {@link #pause()} or {@link #resume()} instead
     */
    @Deprecated
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /**
     * Checks if the simulation is in a terminal state (completed or failed).
     *
     * @return true if status is COMPLETED or FAILED
     */
    public boolean isTerminal() {
        return this.status == SimulationStatus.COMPLETED || this.status == SimulationStatus.FAILED;
    }

    // ========== Contributions (recurring investment top-ups) ==========

    /**
     * True if this simulation can accept a new cash contribution right now.
     * Only LIVE simulations can be topped up — backtests are historical replays
     * and must not have their cash basis mutated mid-flight.
     */
    public boolean canAcceptContribution() {
        return this.status == com.quorumtrading.model.enums.SimulationStatus.LIVE;
    }

    /**
     * Factory for a new {@link Contribution} against this simulation. Enforces
     * the LIVE-state invariant and amount positivity (delegated to
     * {@code Contribution}'s constructor). Persistence of the returned
     * contribution and the matching {@code SimulationPortfolio.recordContribution(...)}
     * call are the caller's responsibility — both must happen in the same
     * transaction.
     *
     * <p>Idempotency is enforced at the database via UNIQUE
     * (source_plan_id, contribution_date). Callers that retry on the same key
     * must catch the unique-violation rather than re-validate here.</p>
     *
     * @param amount             cash being injected (must be positive)
     * @param contributionDate   the as-of date of the contribution; used for TWR
     *                           sub-period chaining and for the idempotency key
     * @param sourcePlanId       optional: the recurring plan that produced this
     *                           contribution (null for manual/ops contributions)
     * @param sourceAllocationId optional: the allocation audit row
     * @return a new {@code Contribution} ready to be persisted
     * @throws IllegalStateException if the simulation is not in LIVE state
     */
    public Contribution buildContribution(
            com.quorumtrading.model.valueobject.Money amount,
            Instant contributionDate,
            java.util.UUID sourcePlanId,
            java.util.UUID sourceAllocationId
    ) {
        if (!canAcceptContribution()) {
            throw new IllegalStateException(
                    String.format("Cannot record contribution on simulation %s: status is %s, must be LIVE",
                            this.simulationId.value(), this.status));
        }
        return new Contribution(
                com.quorumtrading.model.valueobject.ContributionId.generate(),
                this.simulationId,
                amount,
                contributionDate,
                sourcePlanId,
                sourceAllocationId,
                Instant.now()
        );
    }

    /**
     * Checks if the simulation is currently running (not paused and in active state).
     *
     * @return true if simulation is running (SIMULATING or LIVE and not paused)
     */
    public boolean isRunning() {
        return !this.paused && (this.status == SimulationStatus.SIMULATING || this.status == SimulationStatus.LIVE);
    }

    /**
     * Checks if this simulation is a backtest.
     * A simulation is a backtest if and only if it has an end date — the position of that
     * end date relative to the wall clock plays no role. LIVE simulations have no end date
     * (they run indefinitely until ended by the user).
     *
     * <p><b>Backtest Detection Logic:</b></p>
     * <ul>
     *   <li><b>BACKTEST:</b> endDate != null</li>
     *   <li><b>LIVE:</b> endDate == null</li>
     * </ul>
     *
     * <p>Note: a backtest whose replay catches up to the present transitions to LIVE
     * <em>status</em> and keeps evaluating in real time until its end date passes, but it
     * still reports {@code isBacktest() == true} — callers such as trading-context/model
     * selection deliberately keep treating it as a backtest for its whole lifetime.</p>
     *
     * @return true if this simulation has an end date (backtest), false when open-ended (LIVE mode)
     */
    public boolean isBacktest() {
        return endDate != null;
    }

    // ========== Restart From Decision ==========

    /**
     * Restart a simulation from a given progress point, clearing terminal state.
     * Valid from COMPLETED, FAILED, or paused SIMULATING/LIVE.
     * Transitions to SIMULATING + paused so the user can unpause when ready.
     *
     * @param newProgress the progress percentage to restart from (0-100)
     * @throws IllegalStateException if the simulation cannot be restarted from its current state
     */
    public void restartFrom(int newProgress) {
        if (this.status == SimulationStatus.SIMULATING || this.status == SimulationStatus.LIVE) {
            if (!this.paused) {
                throw new IllegalStateException("Cannot restart a running simulation — pause it first");
            }
        } else if (this.status != SimulationStatus.COMPLETED && this.status != SimulationStatus.FAILED) {
            throw new IllegalStateException("Cannot restart simulation from status " + this.status);
        }
        this.status = SimulationStatus.SIMULATING;
        this.paused = true;
        this.completedAt = null;
        this.progress = Math.min(100, Math.max(0, newProgress));
    }

    /**
     * Accumulate archived LLM cost from deleted decisions.
     * Called before deleting consensus decisions during a restart-from operation.
     *
     * @param rawCost the raw LLM cost being archived
     * @param promptTokens prompt tokens being archived
     * @param completionTokens completion tokens being archived
     * @param totalTokens total tokens being archived
     * @param decisionCount number of decisions being archived
     */
    public void archiveLLMCost(BigDecimal rawCost, int promptTokens, int completionTokens, int totalTokens, int decisionCount) {
        this.archivedLLMCostUsd = this.archivedLLMCostUsd.add(rawCost);
        this.archivedLLMPromptTokens += promptTokens;
        this.archivedLLMCompletionTokens += completionTokens;
        this.archivedLLMTotalTokens += totalTokens;
        this.archivedLLMDecisionCount += decisionCount;
    }

    // ========== Backfill State Management ==========

    /**
     * Transitions simulation to WAITING_FOR_DATA state when backfill is triggered.
     * Called by BackfillOrchestratorService when data gaps are detected.
     *
     * @param backfillJobId The ID of the backfill job being executed
     * @throws IllegalStateException if simulation is not in PENDING state
     */
    public void waitForData(java.util.UUID backfillJobId) {
        if (this.status != SimulationStatus.PENDING) {
            throw new IllegalStateException(
                String.format("Cannot transition simulation %s to WAITING_FOR_DATA: not in PENDING state (current status: %s)",
                    this.simulationId.value(), this.status)
            );
        }
        this.status = SimulationStatus.WAITING_FOR_DATA;
        this.backfillJobId = backfillJobId;
    }

    /**
     * Transitions simulation back to PENDING when backfill completes successfully.
     * Called by BackfillCompletedHandler when backfill finishes.
     * Clears the backfill job reference.
     *
     * @throws IllegalStateException if simulation is not in WAITING_FOR_DATA state
     */
    public void dataReady() {
        if (this.status != SimulationStatus.WAITING_FOR_DATA) {
            throw new IllegalStateException(
                String.format("Cannot mark data ready for simulation %s: not in WAITING_FOR_DATA state (current status: %s)",
                    this.simulationId.value(), this.status)
            );
        }
        this.status = SimulationStatus.PENDING;
        this.backfillJobId = null;
    }

    /**
     * Check if this simulation is waiting for market data backfill.
     *
     * @return true if status is WAITING_FOR_DATA
     */
    public boolean isWaitingForData() {
        return this.status == SimulationStatus.WAITING_FOR_DATA;
    }

    // ========== Circuit Breaker for Data Gaps ==========

    /**
     * Increment skipped evaluation counters when consensus is skipped due to missing data.
     * Called by ReachConsensusService when no market data is available.
     */
    public void incrementSkippedEvaluation() {
        this.skippedEvaluationCount++;
        this.totalSkippedEvaluationCount++;
        this.totalEvaluationCount++;
    }

    /**
     * Increment total evaluation count for a successful evaluation.
     * Resets the consecutive skipped count since we had a successful evaluation.
     */
    public void recordSuccessfulEvaluation() {
        this.totalEvaluationCount++;
        this.skippedEvaluationCount = 0;  // Reset consecutive count on success
    }

    /**
     * Reset the consecutive skipped evaluation count.
     * Called when a successful evaluation occurs.
     *
     * @deprecated Use recordSuccessfulEvaluation() instead
     */
    @Deprecated
    public void resetSkippedEvaluationCount() {
        this.skippedEvaluationCount = 0;
    }

    /**
     * Check if the circuit breaker should trigger due to data gaps.
     * Returns true if either threshold is exceeded:
     * - Consecutive skipped evaluations >= consecutiveThreshold
     * - Total skipped percentage >= percentageThreshold
     *
     * @param consecutiveThreshold Maximum consecutive skips allowed (e.g., 5)
     * @param percentageThreshold Maximum skip percentage allowed (e.g., 50)
     * @return true if simulation should pause due to data gaps
     */
    public boolean shouldPauseForDataGap(int consecutiveThreshold, int percentageThreshold) {
        // Check consecutive skips
        if (this.skippedEvaluationCount >= consecutiveThreshold) {
            return true;
        }

        // Check percentage of skipped evaluations (only if we have evaluations)
        if (this.totalEvaluationCount > 0) {
            int skipPercentage = (this.totalSkippedEvaluationCount * 100) / this.totalEvaluationCount;
            if (skipPercentage >= percentageThreshold) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the percentage of skipped evaluations.
     *
     * @return Percentage of evaluations skipped (0-100), or 0 if no evaluations yet
     */
    public int getSkipPercentage() {
        if (this.totalEvaluationCount == 0) {
            return 0;
        }
        return (this.totalSkippedEvaluationCount * 100) / this.totalEvaluationCount;
    }

    // ========== External Pause Management ==========

    /**
     * Check if this simulation can execute (considering both user pause and external pause).
     */
    public boolean canExecute() {
        return !paused && !pausedForExternalReason &&
               (status == SimulationStatus.SIMULATING || status == SimulationStatus.LIVE);
    }

    /**
     * Pause this simulation due to an external reason (e.g., budget depleted).
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
     * Check if this simulation is paused due to an external reason.
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
    public String toString() {
        return "Simulation{" +
                "simulationId=" + simulationId +
                ", name='" + name + '\'' +
                ", strategyId=" + strategyId +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", initialCash=" + initialCash +
                ", benchmarkId=" + benchmarkId +
                ", challengeId=" + challengeId +
                ", brokerAccountId=" + brokerAccountId +
                ", tradingLimit=" + tradingLimit +
                ", status=" + status +
                ", progress=" + progress +
                ", paused=" + paused +
                ", startedAt=" + startedAt +
                ", completedAt=" + completedAt +
                ", createdAt=" + createdAt +
                ", backfillJobId=" + backfillJobId +
                ", skippedEvaluationCount=" + skippedEvaluationCount +
                ", totalSkippedEvaluationCount=" + totalSkippedEvaluationCount +
                ", totalEvaluationCount=" + totalEvaluationCount +
                '}';
    }
}
