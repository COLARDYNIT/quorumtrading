package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.BudgetLimit;
import com.quorumtrading.model.valueobject.BudgetStatus;
import com.quorumtrading.model.valueobject.ModelId;
import com.quorumtrading.model.valueobject.ModelPreferences;
import com.quorumtrading.model.valueobject.MonthlySpend;
import com.quorumtrading.model.valueobject.TradingContext;
import com.quorumtrading.model.valueobject.UserBudgetId;
import com.quorumtrading.model.valueobject.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root for user AI budget management.
 * <p>
 * Budget limit represents a <b>lifetime balance</b>, not a monthly cap.
 * Monthly spend tracking (currentSpend) is kept for display purposes only.
 * Budget availability is determined by lifetimeSpend vs limit.
 * </p>
 *
 * <p><b>Invariants:</b></p>
 * <ul>
 *   <li>Budget limit must be non-negative if set</li>
 *   <li>Spend cannot be negative</li>
 *   <li>Monthly spend resets at the start of each new billing period (display only)</li>
 *   <li>Lifetime spend never resets — it tracks total all-time usage</li>
 *   <li>Events are raised when budget thresholds are crossed</li>
 * </ul>
 *
 * <p><b>Domain Events Raised:</b></p>
 * <ul>
 *   <li>{@code BudgetWarningEvent} - When lifetime spend crosses 80% threshold</li>
 *   <li>{@code BudgetDepletedEvent} - When lifetime spend reaches 100% of limit</li>
 *   <li>{@code BudgetRestoredEvent} - When budget becomes available again (e.g. limit increased)</li>
 * </ul>
 */
public class UserBudget implements AggregateRoot {

    private final UserBudgetId id;
    private final UserId userId;
    private BudgetLimit limit;  // null = unlimited
    private MonthlySpend currentSpend;
    private BigDecimal lifetimeSpend;
    private ModelPreferences modelPreferences;
    private BigDecimal dailySpendLimit; // null = no limit
    private Instant lastTopUpAt; // null = never topped up (free credits only)
    private final Instant createdAt;
    private Instant updatedAt;

    // Pending domain events to be published
    private final List<Object> domainEvents = new ArrayList<>();

    // Track previous status to detect threshold crossings
    private BudgetStatus previousStatus;

    private UserBudget(
        UserBudgetId id,
        UserId userId,
        BudgetLimit limit,
        MonthlySpend currentSpend,
        BigDecimal lifetimeSpend,
        ModelPreferences modelPreferences,
        BigDecimal dailySpendLimit,
        Instant lastTopUpAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "Id cannot be null");
        this.userId = Objects.requireNonNull(userId, "UserId cannot be null");
        this.limit = limit;
        this.currentSpend = currentSpend != null ? currentSpend : MonthlySpend.zero();
        this.lifetimeSpend = lifetimeSpend != null ? lifetimeSpend : BigDecimal.ZERO;
        this.modelPreferences = modelPreferences != null ? modelPreferences : ModelPreferences.EMPTY;
        this.dailySpendLimit = dailySpendLimit;
        this.lastTopUpAt = lastTopUpAt;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.previousStatus = getStatus();
    }

    // ========== Factory Methods ==========

    /**
     * Create a new UserBudget with no limit (unlimited).
     */
    public static UserBudget createUnlimited(UserId userId) {
        return new UserBudget(
            UserBudgetId.generate(),
            userId,
            null,
            MonthlySpend.zero(),
            BigDecimal.ZERO,
            ModelPreferences.EMPTY,
            null,
            null,
            Instant.now(),
            Instant.now()
        );
    }

    /**
     * Create a new UserBudget with a specified limit.
     */
    public static UserBudget createWithLimit(UserId userId, BudgetLimit limit) {
        return new UserBudget(
            UserBudgetId.generate(),
            userId,
            limit,
            MonthlySpend.zero(),
            BigDecimal.ZERO,
            ModelPreferences.EMPTY,
            null,
            null,
            Instant.now(),
            Instant.now()
        );
    }

    /**
     * Reconstitute from persistence.
     */
    public static UserBudget reconstitute(
        UserBudgetId id,
        UserId userId,
        BudgetLimit limit,
        MonthlySpend currentSpend,
        BigDecimal lifetimeSpend,
        ModelPreferences modelPreferences,
        BigDecimal dailySpendLimit,
        Instant lastTopUpAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        return new UserBudget(id, userId, limit, currentSpend, lifetimeSpend, modelPreferences, dailySpendLimit, lastTopUpAt, createdAt, updatedAt);
    }

    // ========== Business Methods ==========

    /**
     * Record LLM usage cost.
     * May raise BudgetWarningEvent or BudgetDepletedEvent.
     *
     * @param cost The cost to record (must be non-negative)
     */
    public void recordUsage(BigDecimal cost) {
        Objects.requireNonNull(cost, "Cost cannot be null");
        if (cost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cost cannot be negative");
        }

        // Ensure we're in the current period
        ensureCurrentPeriod();

        // Store previous status for comparison
        this.previousStatus = getStatus();

        // Add the cost to both monthly (display) and lifetime (budget check)
        this.currentSpend = this.currentSpend.add(cost);
        this.lifetimeSpend = this.lifetimeSpend.add(cost);
        this.updatedAt = Instant.now();

        // Check for threshold crossings
        BudgetStatus newStatus = getStatus();
        checkAndRaiseEvents(newStatus);
    }

    /**
     * Check if budget is available for LLM operations.
     * Based on lifetime spend vs limit (not monthly spend).
     */
    public boolean hasAvailableBudget() {
        if (limit == null) {
            return true; // Unlimited
        }
        return lifetimeSpend.compareTo(limit.amount()) < 0;
    }

    /**
     * Get the current budget status based on lifetime spend.
     */
    public BudgetStatus getStatus() {
        if (limit == null) {
            return BudgetStatus.AVAILABLE;
        }
        if (limit.isExceededBy(lifetimeSpend)) {
            return BudgetStatus.DEPLETED;
        }
        if (limit.isWarningThresholdReached(lifetimeSpend)) {
            return BudgetStatus.WARNING;
        }
        return BudgetStatus.AVAILABLE;
    }

    /**
     * Get the usage percentage (0-100+) based on lifetime spend.
     */
    public BigDecimal getUsagePercentage() {
        if (limit == null) {
            return BigDecimal.ZERO;
        }
        return limit.usagePercentage(lifetimeSpend);
    }

    /**
     * Get remaining budget amount based on lifetime spend.
     */
    public BigDecimal getRemainingBudget() {
        if (limit == null) {
            return null; // Unlimited
        }
        return limit.remaining(lifetimeSpend);
    }

    /**
     * Update the budget limit.
     * May raise BudgetRestoredEvent if previously depleted.
     */
    public void updateLimit(BudgetLimit newLimit) {
        BudgetStatus oldStatus = getStatus();
        this.limit = newLimit;
        this.updatedAt = Instant.now();

        // Check if budget was restored
        BudgetStatus newStatus = getStatus();
        if (oldStatus == BudgetStatus.DEPLETED && newStatus != BudgetStatus.DEPLETED) {
            raiseBudgetRestoredEvent();
        }
    }

    /**
     * Remove the budget limit (make unlimited).
     * Will raise BudgetRestoredEvent if previously depleted.
     */
    public void removeLimit() {
        updateLimit(null);
    }

    /**
     * Set model preference for a trading context.
     */
    public void setModelPreference(TradingContext context, ModelId modelId) {
        Objects.requireNonNull(context, "Context cannot be null");
        this.modelPreferences = this.modelPreferences.withPreference(context, modelId);
        this.updatedAt = Instant.now();
    }

    /**
     * Clear model preference for a trading context.
     */
    public void clearModelPreference(TradingContext context) {
        Objects.requireNonNull(context, "Context cannot be null");
        this.modelPreferences = this.modelPreferences.withoutPreference(context);
        this.updatedAt = Instant.now();
    }

    /**
     * Get the preferred model for a context.
     */
    public Optional<ModelId> getPreferredModel(TradingContext context) {
        return modelPreferences.getFor(context);
    }

    /**
     * Set per-agent model preference.
     */
    public void setAgentModelPreference(TradingContext context, com.quorumtrading.model.enums.AgentRole role, ModelId modelId) {
        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(role, "Role cannot be null");
        this.modelPreferences = this.modelPreferences.withAgentPreference(context, role, modelId);
        this.updatedAt = Instant.now();
    }

    /**
     * Set per-agent model preference for any agent kind — consensus or research.
     */
    public void setAgentModelPreference(TradingContext context, com.quorumtrading.model.enums.AgentKind kind, ModelId modelId) {
        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(kind, "Agent kind cannot be null");
        this.modelPreferences = this.modelPreferences.withAgentPreference(context, kind, modelId);
        this.updatedAt = Instant.now();
    }

    /**
     * Clear per-agent model preference.
     */
    public void clearAgentModelPreference(TradingContext context, com.quorumtrading.model.enums.AgentRole role) {
        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(role, "Role cannot be null");
        this.modelPreferences = this.modelPreferences.withAgentPreference(context, role, null);
        this.updatedAt = Instant.now();
    }

    /**
     * Clear per-agent model preference for any agent kind.
     */
    public void clearAgentModelPreference(TradingContext context, com.quorumtrading.model.enums.AgentKind kind) {
        Objects.requireNonNull(context, "Context cannot be null");
        Objects.requireNonNull(kind, "Agent kind cannot be null");
        this.modelPreferences = this.modelPreferences.withAgentPreference(context, kind, null);
        this.updatedAt = Instant.now();
    }

    /**
     * Set a personal daily spending cap.
     * @param limit the daily limit in USD (must be positive)
     */
    public void setDailySpendLimit(BigDecimal limit) {
        if (limit != null && limit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Daily spend limit must be positive");
        }
        this.dailySpendLimit = limit;
        this.updatedAt = Instant.now();
    }

    /**
     * Remove the daily spending cap.
     */
    public void clearDailySpendLimit() {
        this.dailySpendLimit = null;
        this.updatedAt = Instant.now();
    }

    /**
     * Record the timestamp of the most recent top-up.
     */
    public void recordTopUp() {
        this.lastTopUpAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Reset monthly spend for a new billing period (display only).
     * Budget availability is NOT affected — it's based on lifetime spend.
     */
    public void resetForNewPeriod(YearMonth newPeriod) {
        Objects.requireNonNull(newPeriod, "Period cannot be null");

        if (!newPeriod.equals(currentSpend.period())) {
            this.currentSpend = MonthlySpend.zeroFor(newPeriod);
            this.updatedAt = Instant.now();
        }
    }

    // ========== Event Handling ==========

    private void ensureCurrentPeriod() {
        YearMonth currentPeriod = YearMonth.now();
        if (!currentPeriod.equals(currentSpend.period())) {
            resetForNewPeriod(currentPeriod);
        }
    }

    private void checkAndRaiseEvents(BudgetStatus newStatus) {
        if (previousStatus != newStatus) {
            if (newStatus == BudgetStatus.WARNING && previousStatus == BudgetStatus.AVAILABLE) {
                raiseBudgetWarningEvent();
            } else if (newStatus == BudgetStatus.DEPLETED) {
                raiseBudgetDepletedEvent();
            }
        }
    }

    private void raiseBudgetWarningEvent() {
        domainEvents.add(new BudgetWarningInfo(
            userId,
            limit != null ? limit.amount() : null,
            lifetimeSpend,
            getUsagePercentage()
        ));
    }

    private void raiseBudgetDepletedEvent() {
        domainEvents.add(new BudgetDepletedInfo(
            userId,
            limit != null ? limit.amount() : null,
            lifetimeSpend
        ));
    }

    private void raiseBudgetRestoredEvent() {
        domainEvents.add(new BudgetRestoredInfo(
            userId,
            limit != null ? limit.amount() : null,
            lifetimeSpend,
            currentSpend.period()
        ));
    }

    /**
     * Get and clear pending domain events.
     * Called by the application layer after saving.
     */
    public List<Object> getAndClearDomainEvents() {
        List<Object> events = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return events;
    }

    // ========== Internal Event Info Records ==========

    public record BudgetWarningInfo(UserId userId, BigDecimal limit, BigDecimal spend, BigDecimal percentage) {}
    public record BudgetDepletedInfo(UserId userId, BigDecimal limit, BigDecimal spend) {}
    public record BudgetRestoredInfo(UserId userId, BigDecimal limit, BigDecimal spend, YearMonth period) {}

    // ========== Getters ==========

    public UserBudgetId getId() { return id; }
    public UserId getUserId() { return userId; }
    public Optional<BudgetLimit> getLimit() { return Optional.ofNullable(limit); }
    public MonthlySpend getCurrentSpend() { return currentSpend; }
    public BigDecimal getLifetimeSpend() { return lifetimeSpend; }
    public ModelPreferences getModelPreferences() { return modelPreferences; }
    public Optional<BigDecimal> getDailySpendLimit() { return Optional.ofNullable(dailySpendLimit); }
    public Optional<Instant> getLastTopUpAt() { return Optional.ofNullable(lastTopUpAt); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean hasLimit() { return limit != null; }
    public boolean isUnlimited() { return limit == null; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserBudget that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "UserBudget{" +
            "id=" + id +
            ", userId=" + userId +
            ", limit=" + limit +
            ", lifetimeSpend=" + lifetimeSpend +
            ", currentSpend=" + currentSpend.amount() +
            ", status=" + getStatus() +
            '}';
    }
}
