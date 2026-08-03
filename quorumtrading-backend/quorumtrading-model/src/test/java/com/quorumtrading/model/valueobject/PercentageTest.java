package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class PercentageTest {

    @Test
    void shouldCreateFromDecimal() {
        Percentage percentage = Percentage.fromDecimal(new BigDecimal("0.5"));

        assertThat(percentage.asDecimal()).isEqualByComparingTo("0.5");
        assertThat(percentage.asPercentage()).isEqualByComparingTo("50.0");
    }

    @Test
    void shouldCreateFromPercentage() {
        Percentage percentage = Percentage.fromPercentage(new BigDecimal("75"));

        assertThat(percentage.asDecimal()).isEqualByComparingTo("0.75");
        assertThat(percentage.asPercentage()).isEqualByComparingTo("75.0");
    }

    @Test
    void shouldRejectNegativeValue() {
        assertThatThrownBy(() -> Percentage.fromDecimal(new BigDecimal("-0.1")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Percentage must be between 0 and 1");
    }

    @Test
    void shouldRejectValueGreaterThanOne() {
        assertThatThrownBy(() -> Percentage.fromDecimal(new BigDecimal("1.5")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Percentage must be between 0 and 1");
    }

    @Test
    void shouldMultiplyByMoney() {
        Percentage percentage = Percentage.fromPercentage(new BigDecimal("10"));  // 10%
        Money amount = new Money(new BigDecimal("1000"), Money.Currency.USD);

        Money result = percentage.of(amount);

        assertThat(result.amount()).isEqualByComparingTo("100.0");  // 10% of $1000 = $100
    }
}
