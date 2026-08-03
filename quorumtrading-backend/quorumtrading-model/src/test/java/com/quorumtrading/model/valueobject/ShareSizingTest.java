package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.InvestmentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShareSizingTest {

    private static final BrokerCapabilities FRACTIONAL = BrokerCapabilities.fractional();
    private static final BrokerCapabilities WHOLE_SHARE = BrokerCapabilities.wholeShareEquities();

    // ----- maxAffordableQuantity: fractional path is byte-identical to the old inlined floor -----

    @Test
    void fractionalMaxAffordableIsByteIdenticalToLegacyScale8Floor() {
        Money cash = Money.usd(new BigDecimal("1000.00"));
        Money price = Money.usd(new BigDecimal("150.00"));

        // Legacy inlined behaviour: cash / price floored to 8 decimal places, DOWN.
        BigDecimal legacy = cash.amount().divide(price.amount(), 8, RoundingMode.DOWN);

        BigDecimal actual = ShareSizing.maxAffordableQuantity(
                cash, price, InvestmentType.STOCK, FRACTIONAL);

        // bit-for-bit equal including scale (8) — not just numeric equality
        assertThat(actual).isEqualByComparingTo(legacy);
        assertThat(actual.scale()).isEqualTo(8);
        assertThat(actual).isEqualTo(new BigDecimal("6.66666666"));
    }

    @Test
    void fractionalMaxAffordableMatchesLegacyAcrossRandomishInputs() {
        Money cash = Money.usd(new BigDecimal("12345.67"));
        Money price = Money.usd(new BigDecimal("99.99"));

        BigDecimal legacy = cash.amount().divide(price.amount(), 8, RoundingMode.DOWN);
        BigDecimal actual = ShareSizing.maxAffordableQuantity(
                cash, price, InvestmentType.STOCK, FRACTIONAL);

        assertThat(actual).isEqualTo(legacy);
    }

    @Test
    void fractionalCryptoUsesCryptoFlagAndKeepsScale8() {
        // Whole-share capabilities still allow fractional crypto.
        Money cash = Money.usd(new BigDecimal("100.00"));
        Money price = Money.usd(new BigDecimal("65000.00"));

        BigDecimal actual = ShareSizing.maxAffordableQuantity(
                cash, price, InvestmentType.CRYPTO, WHOLE_SHARE);

        assertThat(actual.scale()).isEqualTo(8);
        assertThat(actual).isEqualByComparingTo(
                cash.amount().divide(price.amount(), 8, RoundingMode.DOWN));
    }

    // ----- maxAffordableQuantity: non-fractional path floors to whole shares -----

    @Test
    void nonFractionalEquityFloorsToWholeShares() {
        Money cash = Money.usd(new BigDecimal("1000.00"));
        Money price = Money.usd(new BigDecimal("150.00"));

        BigDecimal actual = ShareSizing.maxAffordableQuantity(
                cash, price, InvestmentType.STOCK, WHOLE_SHARE);

        // 1000 / 150 = 6.666... -> 6 whole shares, scale 0
        assertThat(actual.scale()).isEqualTo(0);
        assertThat(actual).isEqualByComparingTo(BigDecimal.valueOf(6));
    }

    @Test
    void nonFractionalEquityWithExactlyAffordableQuantityKeepsAllShares() {
        Money cash = Money.usd(new BigDecimal("450.00"));
        Money price = Money.usd(new BigDecimal("150.00"));

        BigDecimal actual = ShareSizing.maxAffordableQuantity(
                cash, price, InvestmentType.STOCK, WHOLE_SHARE);

        assertThat(actual).isEqualByComparingTo(BigDecimal.valueOf(3));
    }

    @Test
    void nonFractionalEquityWithInsufficientCashYieldsZero() {
        Money cash = Money.usd(new BigDecimal("149.99"));
        Money price = Money.usd(new BigDecimal("150.00"));

        BigDecimal actual = ShareSizing.maxAffordableQuantity(
                cash, price, InvestmentType.STOCK, WHOLE_SHARE);

        assertThat(actual).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void worksWithEurCashAndPrice() {
        Money cash = Money.of(new BigDecimal("3000.00"), Money.Currency.EUR);
        Money price = Money.of(new BigDecimal("700.00"), Money.Currency.EUR);

        BigDecimal actual = ShareSizing.maxAffordableQuantity(
                cash, price, InvestmentType.STOCK, WHOLE_SHARE);

        // 3000 / 700 = 4.28... -> 4 whole shares
        assertThat(actual).isEqualByComparingTo(BigDecimal.valueOf(4));
    }

    // ----- roundToLot -----

    @Test
    void roundToLotFractionalKeepsScale8FloorByteIdentical() {
        BigDecimal qty = new BigDecimal("6.123456789");

        BigDecimal actual = ShareSizing.roundToLot(qty, InvestmentType.STOCK, FRACTIONAL);

        assertThat(actual).isEqualTo(qty.setScale(8, RoundingMode.DOWN));
        assertThat(actual.scale()).isEqualTo(8);
        assertThat(actual).isEqualTo(new BigDecimal("6.12345678"));
    }

    @Test
    void roundToLotNonFractionalFloorsToWholeShares() {
        BigDecimal qty = new BigDecimal("6.99");

        BigDecimal actual = ShareSizing.roundToLot(qty, InvestmentType.STOCK, WHOLE_SHARE);

        assertThat(actual.scale()).isEqualTo(0);
        assertThat(actual).isEqualByComparingTo(BigDecimal.valueOf(6));
    }

    @Test
    void roundToLotNonFractionalCryptoStillFractional() {
        BigDecimal qty = new BigDecimal("0.00153456789");

        BigDecimal actual = ShareSizing.roundToLot(qty, InvestmentType.CRYPTO, WHOLE_SHARE);

        assertThat(actual.scale()).isEqualTo(8);
        assertThat(actual).isEqualTo(qty.setScale(8, RoundingMode.DOWN));
    }

    // ----- argument validation -----

    @Test
    void maxAffordableRejectsNullCash() {
        Money price = Money.usd(BigDecimal.TEN);
        assertThatThrownBy(() -> ShareSizing.maxAffordableQuantity(
                null, price, InvestmentType.STOCK, FRACTIONAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cash");
    }

    @Test
    void maxAffordableRejectsNonPositivePrice() {
        Money cash = Money.usd(BigDecimal.TEN);
        Money zeroPrice = Money.usd(BigDecimal.ZERO);
        assertThatThrownBy(() -> ShareSizing.maxAffordableQuantity(
                cash, zeroPrice, InvestmentType.STOCK, FRACTIONAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("price");
    }

    @Test
    void maxAffordableRejectsNullType() {
        Money cash = Money.usd(BigDecimal.TEN);
        Money price = Money.usd(BigDecimal.ONE);
        assertThatThrownBy(() -> ShareSizing.maxAffordableQuantity(
                cash, price, null, FRACTIONAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxAffordableRejectsNullCapabilities() {
        Money cash = Money.usd(BigDecimal.TEN);
        Money price = Money.usd(BigDecimal.ONE);
        assertThatThrownBy(() -> ShareSizing.maxAffordableQuantity(
                cash, price, InvestmentType.STOCK, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capabilities");
    }

    @Test
    void roundToLotRejectsNullQuantity() {
        assertThatThrownBy(() -> ShareSizing.roundToLot(null, InvestmentType.STOCK, FRACTIONAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }
}
