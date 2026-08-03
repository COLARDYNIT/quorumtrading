package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class BudgetLimitTest {

    @Test
    void shouldCreateBudgetLimitWithValidAmount() {
        BudgetLimit limit = new BudgetLimit(new BigDecimal("100.00"), "USD");

        assertThat(limit.amount()).isEqualByComparingTo("100.00");
        assertThat(limit.currency()).isEqualTo("USD");
    }

    @Test
    void shouldThrowWhenAmountIsNull() {
        assertThatThrownBy(() -> new BudgetLimit(null, "USD"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenAmountIsNegative() {
        assertThatThrownBy(() -> new BudgetLimit(new BigDecimal("-10.00"), "USD"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowZeroAmount() {
        BudgetLimit limit = new BudgetLimit(BigDecimal.ZERO, "USD");
        assertThat(limit.amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldDefaultToUsdWhenCurrencyIsNull() {
        BudgetLimit limit = new BudgetLimit(new BigDecimal("100.00"), null);
        assertThat(limit.currency()).isEqualTo("USD");
    }

    @Test
    void shouldDefaultToUsdWhenCurrencyIsBlank() {
        BudgetLimit limit = new BudgetLimit(new BigDecimal("100.00"), "  ");
        assertThat(limit.currency()).isEqualTo("USD");
    }

    @Test
    void shouldCreateUsdLimitWithFactoryMethod() {
        BudgetLimit limit = BudgetLimit.usd(100.00);

        assertThat(limit.amount()).isEqualByComparingTo("100.00");
        assertThat(limit.currency()).isEqualTo("USD");
    }

    @Test
    void shouldCalculateRemainingBudget() {
        BudgetLimit limit = new BudgetLimit(new BigDecimal("100.00"), "USD");

        BigDecimal remaining = limit.remaining(new BigDecimal("30.00"));

        assertThat(remaining).isEqualByComparingTo("70.00");
    }

    @Test
    void shouldReturnZeroWhenSpendExceedsLimit() {
        BudgetLimit limit = new BudgetLimit(new BigDecimal("100.00"), "USD");

        BigDecimal remaining = limit.remaining(new BigDecimal("150.00"));

        assertThat(remaining).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldDetectExceededBudget() {
        BudgetLimit limit = new BudgetLimit(new BigDecimal("100.00"), "USD");

        assertThat(limit.isExceededBy(new BigDecimal("99.99"))).isFalse();
        assertThat(limit.isExceededBy(new BigDecimal("100.00"))).isTrue();
        assertThat(limit.isExceededBy(new BigDecimal("100.01"))).isTrue();
    }

    @Test
    void shouldDetectWarningThreshold() {
        BudgetLimit limit = new BudgetLimit(new BigDecimal("100.00"), "USD");

        assertThat(limit.isWarningThresholdReached(new BigDecimal("79.00"))).isFalse();
        assertThat(limit.isWarningThresholdReached(new BigDecimal("80.00"))).isTrue();
        assertThat(limit.isWarningThresholdReached(new BigDecimal("100.00"))).isTrue();
    }

    @Test
    void shouldCalculateUsagePercentage() {
        BudgetLimit limit = new BudgetLimit(new BigDecimal("100.00"), "USD");

        assertThat(limit.usagePercentage(new BigDecimal("50.00"))).isEqualByComparingTo("50.00");
        assertThat(limit.usagePercentage(new BigDecimal("100.00"))).isEqualByComparingTo("100.00");
        assertThat(limit.usagePercentage(new BigDecimal("150.00"))).isEqualByComparingTo("150.00");
    }
}
