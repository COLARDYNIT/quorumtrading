package com.quorumtrading.application.usecase.budget;

import com.quorumtrading.application.event.BudgetDepletedEvent;
import com.quorumtrading.application.event.BudgetWarningEvent;
import com.quorumtrading.application.exception.BudgetDepletedException;
import com.quorumtrading.application.exception.DailySpendLimitExceededException;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.LLMUsageRepository;
import com.quorumtrading.application.port.out.TransactionRunner;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.service.PlatformMarkup;
import com.quorumtrading.model.valueobject.UsageSource;
import com.quorumtrading.model.valueobject.UserId;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Use case for recording LLM usage and checking budget status.
 */
public class RecordLLMUsageUseCase {

    private static final Logger LOG = Logger.getLogger(RecordLLMUsageUseCase.class);

    private final UserBudgetRepository repository;
    private final EventPublisher<BudgetWarningEvent> warningPublisher;
    private final EventPublisher<BudgetDepletedEvent> depletedPublisher;
    private final LLMUsageRepository llmUsageRepository;
    private final TransactionRunner transactionRunner;

    public RecordLLMUsageUseCase(
        UserBudgetRepository repository,
        EventPublisher<BudgetWarningEvent> warningPublisher,
        EventPublisher<BudgetDepletedEvent> depletedPublisher,
        LLMUsageRepository llmUsageRepository,
        TransactionRunner transactionRunner
    ) {
        this.repository = repository;
        this.warningPublisher = warningPublisher;
        this.depletedPublisher = depletedPublisher;
        this.llmUsageRepository = llmUsageRepository;
        this.transactionRunner = transactionRunner;
    }

    /** The committed budget plus the events it raised, carried out of the transaction together. */
    private record Recorded(UserBudget budget, List<Object> events) {}

    /**
     * Record LLM usage cost for a user and persist to the usage log for daily breakdown.
     *
     * <p>The ledger row and the budget update share <strong>one</strong> transaction. They describe
     * the same event — money spent — and committing them separately is how a budget ends up charged
     * for a call no ledger row records, the drift the V90/V91 migrations exist to repair.</p>
     *
     * <p>A failure is logged at ERROR and counted, then <strong>swallowed</strong>. By this point
     * the model has already answered and the provider has already charged; failing the call would
     * lose the answer and still incur the cost. What the caller is guaranteed is that nothing was
     * half-written: the whole transaction rolled back, so the budget was not moved either.</p>
     *
     * @param userId      The user ID
     * @param billedCost  The billed cost (with markup) to record against budget
     * @param rawCost     The raw cost in USD
     * @param totalTokens Total tokens consumed
     * @param source      The source of the usage (CHAT, SIMULATION, RESEARCH)
     * @return The user's budget — updated on success, unchanged if the write failed
     */
    public UserBudget recordUsage(UserId userId, BigDecimal billedCost, BigDecimal rawCost, int totalTokens, UsageSource source) {
        try {
            Recorded recorded = transactionRunner.inTransaction(() -> {
                llmUsageRepository.logUsage(userId, source, rawCost, totalTokens);

                UserBudget budget = repository.findByUserId(userId)
                    .orElseGet(() -> UserBudget.createUnlimited(userId));
                budget.recordUsage(billedCost);
                UserBudget saved = repository.save(budget);
                // Drained inside the boundary but published outside it: an event announcing a
                // depleted budget must not escape a transaction that then rolls back.
                return new Recorded(saved, budget.getAndClearDomainEvents());
            });

            publishEvents(recorded.events());
            return recorded.budget();

        } catch (RuntimeException e) {
            LOG.errorf(e, "Spend ledger write failed for user %s (source %s, raw $%s, %d tokens) — "
                + "neither the ledger row nor the budget update was committed",
                userId != null ? userId.value() : null, source, rawCost, totalTokens);
            return currentBudgetOf(userId);
        }
    }

    /**
     * The budget as it actually stands after a failed record — deliberately re-read rather than
     * returning the in-memory instance whose increment was rolled back.
     */
    private UserBudget currentBudgetOf(UserId userId) {
        try {
            return transactionRunner.inTransaction(
                () -> repository.findByUserId(userId).orElseGet(() -> UserBudget.createUnlimited(userId)));
        } catch (RuntimeException e) {
            LOG.errorf(e, "Could not re-read budget for user %s after a failed usage record",
                userId != null ? userId.value() : null);
            return UserBudget.createUnlimited(userId);
        }
    }

    /**
     * Check if a user has available budget for LLM operations.
     * Checks both lifetime budget and daily spend limit.
     *
     * @param userId The user ID
     * @return true if budget is available and daily limit not exceeded
     */
    public boolean hasBudgetAvailable(UserId userId) {
        UserBudget budget = repository.findByUserId(userId).orElse(null);
        if (budget == null) {
            return true; // No budget configured = unlimited
        }
        if (!budget.hasAvailableBudget()) {
            return false;
        }
        if (budget.getDailySpendLimit().isPresent()) {
            BigDecimal todayBilled = getTodayBilledSpend(userId);
            if (todayBilled.compareTo(budget.getDailySpendLimit().get()) >= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Pre-check before LLM operation.
     * Throws exception if budget is depleted or daily limit exceeded.
     *
     * @param userId The user ID
     * @throws BudgetDepletedException if lifetime budget is depleted
     * @throws DailySpendLimitExceededException if daily spend limit is exceeded
     */
    public void requireAvailableBudget(UserId userId) {
        UserBudget budget = repository.findByUserId(userId).orElse(null);
        if (budget == null) {
            return; // No budget configured = unlimited
        }
        if (!budget.hasAvailableBudget()) {
            throw new BudgetDepletedException(userId);
        }
        if (budget.getDailySpendLimit().isPresent()) {
            BigDecimal dailyLimit = budget.getDailySpendLimit().get();
            BigDecimal todayBilled = getTodayBilledSpend(userId);
            if (todayBilled.compareTo(dailyLimit) >= 0) {
                throw new DailySpendLimitExceededException(userId, dailyLimit, todayBilled);
            }
        }
    }

    private BigDecimal getTodayBilledSpend(UserId userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        BigDecimal rawCost = llmUsageRepository.getTotalRawCost(userId, today, today);
        if (rawCost == null) {
            return BigDecimal.ZERO;
        }
        // Scale 8 matches llm_usage_log.raw_cost_usd DECIMAL(18,8) and the two read-side use cases;
        // a daily cap compared at a coarser scale than the ledger it reads is a different number.
        return rawCost.multiply(PlatformMarkup.rate()).setScale(8, RoundingMode.HALF_UP);
    }

    private void publishEvents(List<Object> events) {
        for (Object event : events) {
            if (event instanceof UserBudget.BudgetWarningInfo info) {
                warningPublisher.publish(
                    BudgetWarningEvent.of(info.userId(), info.limit(), info.spend(), info.percentage())
                );
            } else if (event instanceof UserBudget.BudgetDepletedInfo info) {
                depletedPublisher.publish(
                    BudgetDepletedEvent.of(info.userId(), info.limit(), info.spend())
                );
            }
        }
    }

}
