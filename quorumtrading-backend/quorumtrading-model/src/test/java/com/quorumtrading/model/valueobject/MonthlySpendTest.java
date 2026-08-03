package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.YearMonth;
import static org.assertj.core.api.Assertions.*;

class MonthlySpendTest {

    @Test
    void shouldCreateMonthlySpendWithValidData() {
        YearMonth period = YearMonth.of(2025, 1);
        MonthlySpend spend = new MonthlySpend(new BigDecimal("50.00"), period);

        assertThat(spend.amount()).isEqualByComparingTo("50.00");
        assertThat(spend.period()).isEqualTo(period);
    }

    @Test
    void shouldDefaultToZeroWhenAmountIsNull() {
        MonthlySpend spend = new MonthlySpend(null, YearMonth.now());
        assertThat(spend.amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldThrowWhenAmountIsNegative() {
        assertThatThrownBy(() -> new MonthlySpend(new BigDecimal("-10.00"), YearMonth.now()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowZeroAmount() {
        MonthlySpend spend = new MonthlySpend(BigDecimal.ZERO, YearMonth.now());
        assertThat(spend.amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldDefaultToCurrentPeriodWhenNull() {
        MonthlySpend spend = new MonthlySpend(new BigDecimal("50.00"), null);
        assertThat(spend.period()).isEqualTo(YearMonth.now());
    }

    @Test
    void shouldCreateZeroSpendForCurrentMonth() {
        MonthlySpend spend = MonthlySpend.zero();

        assertThat(spend.amount()).isEqualByComparingTo("0.00");
        assertThat(spend.period()).isEqualTo(YearMonth.now());
    }

    @Test
    void shouldCreateZeroSpendForSpecificPeriod() {
        YearMonth period = YearMonth.of(2025, 6);
        MonthlySpend spend = MonthlySpend.zeroFor(period);

        assertThat(spend.amount()).isEqualByComparingTo("0.00");
        assertThat(spend.period()).isEqualTo(period);
    }

    @Test
    void shouldAddAmount() {
        YearMonth period = YearMonth.of(2025, 1);
        MonthlySpend spend = new MonthlySpend(new BigDecimal("50.00"), period);

        MonthlySpend updated = spend.add(new BigDecimal("25.00"));

        assertThat(updated.amount()).isEqualByComparingTo("75.00");
        assertThat(updated.period()).isEqualTo(period);
    }

    @Test
    void shouldDetectCurrentPeriod() {
        MonthlySpend currentPeriod = MonthlySpend.zero();
        MonthlySpend pastPeriod = new MonthlySpend(BigDecimal.ZERO, YearMonth.now().minusMonths(1));

        assertThat(currentPeriod.isCurrentPeriod()).isTrue();
        assertThat(pastPeriod.isCurrentPeriod()).isFalse();
    }

    @Test
    void shouldCheckIfLessThanLimit() {
        MonthlySpend spend = new MonthlySpend(new BigDecimal("50.00"), YearMonth.now());
        BudgetLimit limit = BudgetLimit.usd(100.00);

        assertThat(spend.isLessThan(limit)).isTrue();
    }

    @Test
    void shouldReturnTrueWhenNoLimit() {
        MonthlySpend spend = new MonthlySpend(new BigDecimal("1000.00"), YearMonth.now());

        assertThat(spend.isLessThan(null)).isTrue();
    }

    @Test
    void shouldGetCorrectStatusForLimit() {
        BudgetLimit limit = BudgetLimit.usd(100.00);

        assertThat(new MonthlySpend(new BigDecimal("50.00"), YearMonth.now()).getStatusFor(limit))
            .isEqualTo(BudgetStatus.AVAILABLE);

        assertThat(new MonthlySpend(new BigDecimal("80.00"), YearMonth.now()).getStatusFor(limit))
            .isEqualTo(BudgetStatus.WARNING);

        assertThat(new MonthlySpend(new BigDecimal("100.00"), YearMonth.now()).getStatusFor(limit))
            .isEqualTo(BudgetStatus.DEPLETED);
    }

    @Test
    void shouldReturnAvailableWhenNoLimit() {
        MonthlySpend spend = new MonthlySpend(new BigDecimal("1000.00"), YearMonth.now());

        assertThat(spend.getStatusFor(null)).isEqualTo(BudgetStatus.AVAILABLE);
    }
}
