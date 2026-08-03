package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class UserBudgetTest {

    private UserId createUserId() {
        return new UserId(UUID.randomUUID());
    }

    @Test
    void shouldCreateUnlimitedUserBudget() {
        UserBudget budget = UserBudget.createUnlimited(createUserId());

        assertThat(budget.getId()).isNotNull();
        assertThat(budget.getUserId()).isNotNull();
        assertThat(budget.getLimit()).isEmpty(); // No limit
        assertThat(budget.getCurrentSpend()).isNotNull();
        assertThat(budget.getCurrentSpend().amount()).isEqualByComparingTo("0.00");
        assertThat(budget.getLifetimeSpend()).isEqualByComparingTo("0.00");
        assertThat(budget.getModelPreferences()).isNotNull();
    }

    @Test
    void shouldCreateUserBudgetWithLimit() {
        BudgetLimit limit = BudgetLimit.usd(100.00);
        UserBudget budget = UserBudget.createWithLimit(createUserId(), limit);

        assertThat(budget.getLimit()).isPresent();
        assertThat(budget.getLimit().get().amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldRecordUsage() {
        UserBudget budget = UserBudget.createUnlimited(createUserId());

        budget.recordUsage(new BigDecimal("10.00"));

        assertThat(budget.getCurrentSpend().amount()).isEqualByComparingTo("10.00");
        assertThat(budget.getLifetimeSpend()).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldAccumulateUsage() {
        UserBudget budget = UserBudget.createUnlimited(createUserId());

        budget.recordUsage(new BigDecimal("10.00"));
        budget.recordUsage(new BigDecimal("5.00"));

        assertThat(budget.getCurrentSpend().amount()).isEqualByComparingTo("15.00");
        assertThat(budget.getLifetimeSpend()).isEqualByComparingTo("15.00");
    }

    @Test
    void shouldThrowWhenRecordingNegativeUsage() {
        UserBudget budget = UserBudget.createUnlimited(createUserId());

        assertThatThrownBy(() -> budget.recordUsage(new BigDecimal("-5.00")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnTrueForHasAvailableBudgetWhenNoLimit() {
        UserBudget budget = UserBudget.createUnlimited(createUserId());
        budget.recordUsage(new BigDecimal("1000000.00"));

        assertThat(budget.hasAvailableBudget()).isTrue();
    }

    @Test
    void shouldReturnTrueForHasAvailableBudgetWhenUnderLimit() {
        BudgetLimit limit = BudgetLimit.usd(100.00);
        UserBudget budget = UserBudget.createWithLimit(createUserId(), limit);
        budget.recordUsage(new BigDecimal("50.00"));

        assertThat(budget.hasAvailableBudget()).isTrue();
    }

    @Test
    void shouldReturnFalseForHasAvailableBudgetWhenAtLimit() {
        BudgetLimit limit = BudgetLimit.usd(100.00);
        UserBudget budget = UserBudget.createWithLimit(createUserId(), limit);
        budget.recordUsage(new BigDecimal("100.00"));

        assertThat(budget.hasAvailableBudget()).isFalse();
    }

    @Test
    void shouldReturnFalseForHasAvailableBudgetWhenOverLimit() {
        BudgetLimit limit = BudgetLimit.usd(100.00);
        UserBudget budget = UserBudget.createWithLimit(createUserId(), limit);
        budget.recordUsage(new BigDecimal("150.00"));

        assertThat(budget.hasAvailableBudget()).isFalse();
    }

    @Test
    void shouldReturnAvailableStatusWhenNoLimit() {
        UserBudget budget = UserBudget.createUnlimited(createUserId());

        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.AVAILABLE);
    }

    @Test
    void shouldReturnAvailableStatusWhenUnder80Percent() {
        BudgetLimit limit = BudgetLimit.usd(100.00);
        UserBudget budget = UserBudget.createWithLimit(createUserId(), limit);
        budget.recordUsage(new BigDecimal("79.00"));

        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.AVAILABLE);
    }

    @Test
    void shouldReturnWarningStatusWhenAt80Percent() {
        BudgetLimit limit = BudgetLimit.usd(100.00);
        UserBudget budget = UserBudget.createWithLimit(createUserId(), limit);
        budget.recordUsage(new BigDecimal("80.00"));

        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.WARNING);
    }

    @Test
    void shouldReturnWarningStatusWhenAt99Percent() {
        BudgetLimit limit = BudgetLimit.usd(100.00);
        UserBudget budget = UserBudget.createWithLimit(createUserId(), limit);
        budget.recordUsage(new BigDecimal("99.00"));

        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.WARNING);
    }

    @Test
    void shouldReturnDepletedStatusWhenAt100Percent() {
        BudgetLimit limit = BudgetLimit.usd(100.00);
        UserBudget budget = UserBudget.createWithLimit(createUserId(), limit);
        budget.recordUsage(new BigDecimal("100.00"));

        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.DEPLETED);
    }

    @Test
    void shouldReturnDepletedStatusWhenOver100Percent() {
        BudgetLimit limit = BudgetLimit.usd(100.00);
        UserBudget budget = UserBudget.createWithLimit(createUserId(), limit);
        budget.recordUsage(new BigDecimal("150.00"));

        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.DEPLETED);
    }

    @Test
    void shouldSetModelPreference() {
        UserBudget budget = UserBudget.createUnlimited(createUserId());
        ModelId modelId = new ModelId("claude-3-opus");

        budget.setModelPreference(TradingContext.LIVE_TRADING, modelId);

        assertThat(budget.getPreferredModel(TradingContext.LIVE_TRADING))
            .isPresent()
            .contains(modelId);
    }

    @Test
    void shouldClearModelPreference() {
        UserBudget budget = UserBudget.createUnlimited(createUserId());
        ModelId modelId = new ModelId("claude-3-opus");

        budget.setModelPreference(TradingContext.LIVE_TRADING, modelId);
        budget.clearModelPreference(TradingContext.LIVE_TRADING);

        assertThat(budget.getPreferredModel(TradingContext.LIVE_TRADING)).isEmpty();
    }

    @Test
    void shouldReturnDomainEventsAfterRecordingUsageThatTriggersWarning() {
        BudgetLimit limit = BudgetLimit.usd(100.00);
        UserBudget budget = UserBudget.createWithLimit(createUserId(), limit);

        // Record usage that triggers warning (80%)
        budget.recordUsage(new BigDecimal("80.00"));
        List<Object> events = budget.getAndClearDomainEvents();

        assertThat(events).isNotEmpty();
        assertThat(events.get(0)).isInstanceOf(UserBudget.BudgetWarningInfo.class);
    }

    @Test
    void shouldReturnDomainEventsAfterRecordingUsageThatTriggersDepleted() {
        BudgetLimit limit = BudgetLimit.usd(100.00);
        UserBudget budget = UserBudget.createWithLimit(createUserId(), limit);

        // Record usage that triggers depleted (100%)
        budget.recordUsage(new BigDecimal("100.00"));
        List<Object> events = budget.getAndClearDomainEvents();

        assertThat(events).isNotEmpty();
        assertThat(events.get(0)).isInstanceOf(UserBudget.BudgetDepletedInfo.class);
    }

    @Test
    void shouldClearDomainEventsAfterGetting() {
        BudgetLimit limit = BudgetLimit.usd(100.00);
        UserBudget budget = UserBudget.createWithLimit(createUserId(), limit);
        budget.recordUsage(new BigDecimal("80.00"));

        budget.getAndClearDomainEvents(); // First call clears events
        List<Object> secondCall = budget.getAndClearDomainEvents();

        assertThat(secondCall).isEmpty();
    }

    @Test
    void shouldUpdateLimit() {
        UserBudget budget = UserBudget.createUnlimited(createUserId());
        BudgetLimit newLimit = BudgetLimit.usd(200.00);

        budget.updateLimit(newLimit);

        assertThat(budget.getLimit()).isPresent();
        assertThat(budget.getLimit().get().amount()).isEqualByComparingTo("200.00");
    }

    @Test
    void shouldRemoveLimit() {
        BudgetLimit limit = BudgetLimit.usd(100.00);
        UserBudget budget = UserBudget.createWithLimit(createUserId(), limit);

        budget.removeLimit();

        assertThat(budget.getLimit()).isEmpty();
        assertThat(budget.isUnlimited()).isTrue();
    }

    @Test
    void shouldCalculateUsagePercentage() {
        BudgetLimit limit = BudgetLimit.usd(100.00);
        UserBudget budget = UserBudget.createWithLimit(createUserId(), limit);
        budget.recordUsage(new BigDecimal("50.00"));

        assertThat(budget.getUsagePercentage()).isEqualByComparingTo("50.00");
    }

    @Test
    void shouldCalculateRemainingBudget() {
        BudgetLimit limit = BudgetLimit.usd(100.00);
        UserBudget budget = UserBudget.createWithLimit(createUserId(), limit);
        budget.recordUsage(new BigDecimal("30.00"));

        assertThat(budget.getRemainingBudget()).isEqualByComparingTo("70.00");
    }

    @Test
    void shouldReturnNullForRemainingBudgetWhenUnlimited() {
        UserBudget budget = UserBudget.createUnlimited(createUserId());
        budget.recordUsage(new BigDecimal("1000.00"));

        assertThat(budget.getRemainingBudget()).isNull();
    }

    @Test
    void shouldNotResetAvailabilityOnNewPeriod() {
        BudgetLimit limit = BudgetLimit.usd(100.00);
        UserBudget budget = UserBudget.createWithLimit(createUserId(), limit);

        // Spend the full budget
        budget.recordUsage(new BigDecimal("100.00"));
        assertThat(budget.hasAvailableBudget()).isFalse();
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.DEPLETED);

        // Reset for new month — monthly spend resets but budget stays depleted
        YearMonth nextMonth = YearMonth.now().plusMonths(1);
        budget.resetForNewPeriod(nextMonth);

        // Monthly spend is reset (display only)
        assertThat(budget.getCurrentSpend().amount()).isEqualByComparingTo("0.00");
        assertThat(budget.getCurrentSpend().period()).isEqualTo(nextMonth);

        // Lifetime spend and budget availability are NOT reset
        assertThat(budget.getLifetimeSpend()).isEqualByComparingTo("100.00");
        assertThat(budget.hasAvailableBudget()).isFalse();
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.DEPLETED);
        assertThat(budget.getRemainingBudget()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldTrackLifetimeSpendAcrossPeriodResets() {
        BudgetLimit limit = BudgetLimit.usd(100.00);
        UserBudget budget = UserBudget.createWithLimit(createUserId(), limit);

        // Spend $30 in first month
        budget.recordUsage(new BigDecimal("30.00"));
        assertThat(budget.getLifetimeSpend()).isEqualByComparingTo("30.00");
        assertThat(budget.getRemainingBudget()).isEqualByComparingTo("70.00");

        // Reset for next month
        budget.resetForNewPeriod(YearMonth.now().plusMonths(1));
        assertThat(budget.getCurrentSpend().amount()).isEqualByComparingTo("0.00");
        assertThat(budget.getLifetimeSpend()).isEqualByComparingTo("30.00");
        assertThat(budget.getRemainingBudget()).isEqualByComparingTo("70.00");

        // Spend $50 more in second month
        budget.recordUsage(new BigDecimal("50.00"));
        assertThat(budget.getCurrentSpend().amount()).isEqualByComparingTo("50.00");
        assertThat(budget.getLifetimeSpend()).isEqualByComparingTo("80.00");
        assertThat(budget.getRemainingBudget()).isEqualByComparingTo("20.00");
        assertThat(budget.getStatus()).isEqualTo(BudgetStatus.WARNING);
    }
}
