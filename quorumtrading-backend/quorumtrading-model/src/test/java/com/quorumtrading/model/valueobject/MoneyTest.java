package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    @Test
    void shouldCreateMoneyWithValidAmount() {
        Money money = new Money(new BigDecimal("100.00"), Money.Currency.USD);

        assertThat(money.amount()).isEqualByComparingTo("100.00");
        assertThat(money.currency()).isEqualTo(Money.Currency.USD);
    }

    @Test
    void shouldAddMoneyWithSameCurrency() {
        Money money1 = new Money(new BigDecimal("100.00"), Money.Currency.USD);
        Money money2 = new Money(new BigDecimal("50.00"), Money.Currency.USD);

        Money result = money1.add(money2);

        assertThat(result.amount()).isEqualByComparingTo("150.00");
    }

    @Test
    void shouldThrowWhenAddingDifferentCurrencies() {
        Money usd = new Money(new BigDecimal("100.00"), Money.Currency.USD);
        Money eur = new Money(new BigDecimal("100.00"), Money.Currency.EUR);

        assertThatThrownBy(() -> usd.add(eur))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot operate on different currencies");
    }

    @Test
    void shouldMultiplyByBigDecimal() {
        Money money = new Money(new BigDecimal("100.00"), Money.Currency.USD);

        Money result = money.multiply(new BigDecimal("0.5"));

        assertThat(result.amount()).isEqualByComparingTo("50.00");
    }

    @Test
    void shouldFormatUsdAsDollarPrefixedTwoDecimals() {
        assertThat(new Money(new BigDecimal("200.00000000"), Money.Currency.USD).format())
            .isEqualTo("$200.00");
        assertThat(new Money(new BigDecimal("1234.5"), Money.Currency.USD).format())
            .isEqualTo("$1234.50");
    }

    @Test
    void shouldFormatEurWithEuroSign() {
        assertThat(new Money(new BigDecimal("99.999"), Money.Currency.EUR).format())
            .isEqualTo("€100.00"); // HALF_UP at 2dp
    }

    @Test
    void shouldFormatBtcWithEightDecimalsAndSuffix() {
        assertThat(new Money(new BigDecimal("0.054"), Money.Currency.BTC).format())
            .isEqualTo("0.05400000 BTC");
    }

    @Test
    void formatShouldNotMatchRecordToStringDefault() {
        // Regression guard: the original notification bug was Money.toString() leaking
        // ("Money[amount=200.00000000, currency=USD]") into user-facing copy. Confirm
        // .format() never returns anything resembling that representation.
        Money m = new Money(new BigDecimal("200"), Money.Currency.USD);
        assertThat(m.format()).doesNotContain("Money[", "amount=", "currency=");
    }
}
