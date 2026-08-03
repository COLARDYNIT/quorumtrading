package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.InvestmentPlanStatus;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A recurring investment plan — the "C" in DCA at aggregate level.
 *
 * <p><b>Aggregate Root.</b> Each plan owns its execution schedule, budget rules,
 * and strategy-selection policy. Allocations (audit-trail rows) are a separate
 * aggregate referenced by {@link InvestmentPlanId}.</p>
 *
 * <p><b>State machine:</b>
 * {@code ACTIVE} ⇄ {@code PAUSED}, both → {@code TERMINATED} (terminal).</p>
 *
 * <p><b>Schedule:</b> Phase 3 ships monthly cadence keyed on {@code dayOfMonth}.
 * Days 1-28 only (avoids month-end edge cases for 29/30/31).</p>
 *
 * <p><b>Invariants:</b></p>
 * <ul>
 *   <li>{@code amount} positive when {@code budgetMode != ALL_AVAILABLE}.</li>
 *   <li>{@code dayOfMonth} in [1, 28].</li>
 *   <li>No ticker appears in both {@code allowedTickers} and {@code blockedTickers}.</li>
 *   <li>{@code nextExecutionAt} non-null while {@code status == ACTIVE}.</li>
 *   <li>{@code TERMINATED} is terminal — no transitions back.</li>
 * </ul>
 */
public class InvestmentPlan implements AggregateRoot {

    private static final int MAX_DAY_OF_MONTH = 28;

    private final InvestmentPlanId id;
    private final UserId userId;
    private BrokerAccountId brokerAccountId;
    private BudgetMode budgetMode;
    private Money amount;                 // ignored when budgetMode == ALL_AVAILABLE
    private int dayOfMonth;
    private PickerPolicy pickerPolicy;
    private InvestmentPlanPreferences preferences;
    private InvestmentPlanStatus status;
    private Instant nextExecutionAt;
    private Instant lastExecutionAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private InvestmentPlan(InvestmentPlanId id, UserId userId, BrokerAccountId brokerAccountId,
                           BudgetMode budgetMode, Money amount, int dayOfMonth,
                           PickerPolicy pickerPolicy, InvestmentPlanPreferences preferences,
                           InvestmentPlanStatus status, Instant nextExecutionAt,
                           Instant lastExecutionAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.brokerAccountId = brokerAccountId;
        this.budgetMode = budgetMode;
        this.amount = amount;
        this.dayOfMonth = dayOfMonth;
        this.pickerPolicy = pickerPolicy;
        this.preferences = preferences == null ? InvestmentPlanPreferences.EMPTY : preferences;
        this.status = status;
        this.nextExecutionAt = nextExecutionAt;
        this.lastExecutionAt = lastExecutionAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static InvestmentPlan create(InvestmentPlanId id, UserId userId,
                                        BrokerAccountId brokerAccountId,
                                        BudgetMode budgetMode, Money amount,
                                        int dayOfMonth, PickerPolicy pickerPolicy,
                                        InvestmentPlanPreferences preferences,
                                        Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(brokerAccountId, "brokerAccountId");
        Objects.requireNonNull(budgetMode, "budgetMode");
        Objects.requireNonNull(pickerPolicy, "pickerPolicy");
        Objects.requireNonNull(now, "now");
        validateDayOfMonth(dayOfMonth);
        validateTickerPolicy(preferences);
        if (budgetMode != BudgetMode.ALL_AVAILABLE) {
            if (amount == null || !amount.isPositive()) {
                throw new IllegalArgumentException(
                        "amount must be positive for budgetMode " + budgetMode);
            }
        }

        Instant next = computeNextExecution(dayOfMonth, now);
        return new InvestmentPlan(id, userId, brokerAccountId, budgetMode, amount, dayOfMonth,
                pickerPolicy, preferences, InvestmentPlanStatus.ACTIVE,
                next, null, now, now);
    }

    public static InvestmentPlan reconstitute(InvestmentPlanId id, UserId userId,
                                              BrokerAccountId brokerAccountId,
                                              BudgetMode budgetMode, Money amount,
                                              int dayOfMonth, PickerPolicy pickerPolicy,
                                              InvestmentPlanPreferences preferences,
                                              InvestmentPlanStatus status,
                                              Instant nextExecutionAt, Instant lastExecutionAt,
                                              Instant createdAt, Instant updatedAt) {
        return new InvestmentPlan(id, userId, brokerAccountId, budgetMode, amount, dayOfMonth,
                pickerPolicy, preferences, status, nextExecutionAt, lastExecutionAt,
                createdAt, updatedAt);
    }

    private static void validateDayOfMonth(int dayOfMonth) {
        if (dayOfMonth < 1 || dayOfMonth > MAX_DAY_OF_MONTH) {
            throw new IllegalArgumentException(
                    "dayOfMonth must be between 1 and 28 (got " + dayOfMonth + ")");
        }
    }

    /**
     * Refuse a ticker policy that lists the same ticker as both allowed and blocked.
     *
     * <p>{@link InvestmentPlanPreferences#isTickerAllowed(String)} resolves the collision by letting
     * the block win, so a ticker in both lists is simply unreachable. When <em>every</em> allowed
     * ticker is also blocked the permitted universe is empty and the plan can never fund anything —
     * and since automatic execution is off by default on most instances, such a plan sits ACTIVE with
     * a {@code nextExecutionAt} date and looks healthy indefinitely. Even when it does fire, the only
     * feedback is a per-strategy rejection ("strategy trades disallowed tickers"), which blames the
     * strategy for a fault in the plan's own configuration.
     *
     * <p>Shared by {@link #create} and {@link #updatePreferences} — same arrangement as
     * {@link #validateDayOfMonth} — so an update cannot introduce what creation refuses.
     * {@link #reconstitute} deliberately skips it: plans already stored with the contradiction must
     * stay readable so their owner can repair or delete them.
     */
    private static void validateTickerPolicy(InvestmentPlanPreferences preferences) {
        if (preferences == null) {
            return;
        }
        List<String> blocked = preferences.blockedTickers();
        if (blocked.isEmpty()) {
            return;
        }
        List<String> allowed = preferences.allowedTickers().stream()
                .map(t -> t.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        List<String> conflicting = allowed.stream()
                .filter(a -> blocked.stream().anyMatch(b -> b.equalsIgnoreCase(a)))
                .toList();
        if (conflicting.isEmpty()) {
            return;
        }

        String named = String.join(", ", conflicting);
        // The two cases need different remedies: the total case leaves nothing investable and the
        // allowlist itself has to change, while a partial overlap only silences the named tickers.
        String consequence = conflicting.size() == allowed.size()
                ? "every allowed ticker is blocked: the policy permits nothing and the plan could "
                        + "never fund a strategy. Drop " + named + " from blockedTickers, or allow "
                        + "a ticker you have not blocked."
                : named + " can never be traded, though the rest of allowedTickers still can. "
                        + "Remove " + named + " from one of the two lists.";
        throw new IllegalArgumentException(
                "Contradictory ticker policy — " + named + " in both allowedTickers and "
                        + "blockedTickers, and a block always wins, so " + consequence
                        + " The fault is in the plan's ticker policy, not in any strategy.");
    }

    // ---- State transitions ----

    public void pause(Instant now) {
        assertNotTerminated();
        if (this.status == InvestmentPlanStatus.PAUSED) {
            return;
        }
        this.status = InvestmentPlanStatus.PAUSED;
        this.updatedAt = now;
    }

    public void resume(Instant now) {
        assertNotTerminated();
        if (this.status == InvestmentPlanStatus.ACTIVE) {
            return;
        }
        this.status = InvestmentPlanStatus.ACTIVE;
        // Recompute next-execution-at from now; the old value may be in the past.
        this.nextExecutionAt = computeNextExecution(dayOfMonth, now);
        this.updatedAt = now;
    }

    public void terminate(Instant now) {
        if (this.status == InvestmentPlanStatus.TERMINATED) {
            return;
        }
        this.status = InvestmentPlanStatus.TERMINATED;
        this.nextExecutionAt = null;
        this.updatedAt = now;
    }

    /** True if a scheduled execution is due at {@code now} (or earlier) and the plan is active. */
    public boolean isDueAt(Instant now) {
        return status == InvestmentPlanStatus.ACTIVE
                && nextExecutionAt != null
                && !nextExecutionAt.isAfter(now);
    }

    /**
     * Called by the executor after a successful or skipped attempt — advances
     * {@code nextExecutionAt} to the next scheduled date and stamps {@code lastExecutionAt}.
     * A retry after a transient failure should NOT call this; the next firing is the same day.
     */
    public void recordExecutionTick(Instant executedAt) {
        assertNotTerminated();
        this.lastExecutionAt = executedAt;
        this.nextExecutionAt = computeNextExecutionAfter(dayOfMonth, executedAt);
        this.updatedAt = executedAt;
    }

    // ---- Mutators ----

    public void updateBudget(BudgetMode budgetMode, Money amount, Instant now) {
        assertNotTerminated();
        Objects.requireNonNull(budgetMode, "budgetMode");
        if (budgetMode != BudgetMode.ALL_AVAILABLE && (amount == null || !amount.isPositive())) {
            throw new IllegalArgumentException("amount must be positive for budgetMode " + budgetMode);
        }
        this.budgetMode = budgetMode;
        this.amount = amount;
        this.updatedAt = now;
    }

    public void updateSchedule(int dayOfMonth, Instant now) {
        assertNotTerminated();
        validateDayOfMonth(dayOfMonth);
        this.dayOfMonth = dayOfMonth;
        if (status == InvestmentPlanStatus.ACTIVE) {
            this.nextExecutionAt = computeNextExecution(dayOfMonth, now);
        }
        this.updatedAt = now;
    }

    public void updatePreferences(InvestmentPlanPreferences preferences, Instant now) {
        assertNotTerminated();
        validateTickerPolicy(preferences);
        this.preferences = preferences == null ? InvestmentPlanPreferences.EMPTY : preferences;
        this.updatedAt = now;
    }

    public void updateBrokerAccount(BrokerAccountId brokerAccountId, Instant now) {
        assertNotTerminated();
        Objects.requireNonNull(brokerAccountId, "brokerAccountId");
        this.brokerAccountId = brokerAccountId;
        this.updatedAt = now;
    }

    // ---- Helpers ----

    private void assertNotTerminated() {
        if (status == InvestmentPlanStatus.TERMINATED) {
            throw new IllegalStateException(
                    "Investment plan " + id.value() + " is TERMINATED and cannot be modified");
        }
    }

    /**
     * Resolve next execution time for a freshly-created or resumed plan: today if the
     * day-of-month hasn't passed yet, otherwise the same day next month.
     */
    static Instant computeNextExecution(int dayOfMonth, Instant now) {
        LocalDate today = now.atOffset(ZoneOffset.UTC).toLocalDate();
        LocalDate target = LocalDate.of(today.getYear(), today.getMonth(),
                Math.min(dayOfMonth, today.lengthOfMonth()));
        if (!target.isAfter(today)) {
            // If the target is today, fire today; if earlier in the month, jump to next month.
            if (target.equals(today)) {
                return target.atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            YearMonth nextMonth = YearMonth.from(today).plusMonths(1);
            target = nextMonth.atDay(Math.min(dayOfMonth, nextMonth.lengthOfMonth()));
        }
        return target.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /**
     * Advance from a fired tick — always lands on the dayOfMonth of the NEXT month,
     * regardless of whether the prior tick was a few hours late.
     */
    static Instant computeNextExecutionAfter(int dayOfMonth, Instant executedAt) {
        LocalDate executed = executedAt.atOffset(ZoneOffset.UTC).toLocalDate();
        YearMonth nextMonth = YearMonth.from(executed).plusMonths(1);
        LocalDate target = nextMonth.atDay(Math.min(dayOfMonth, nextMonth.lengthOfMonth()));
        return target.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    // ---- Accessors ----

    public InvestmentPlanId getId() { return id; }
    public UserId getUserId() { return userId; }
    public BrokerAccountId getBrokerAccountId() { return brokerAccountId; }
    public BudgetMode getBudgetMode() { return budgetMode; }
    public Money getAmount() { return amount; }
    public int getDayOfMonth() { return dayOfMonth; }
    public PickerPolicy getPickerPolicy() { return pickerPolicy; }
    public InvestmentPlanPreferences getPreferences() { return preferences; }
    public InvestmentPlanStatus getStatus() { return status; }
    public Instant getNextExecutionAt() { return nextExecutionAt; }
    public Instant getLastExecutionAt() { return lastExecutionAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestmentPlan that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
