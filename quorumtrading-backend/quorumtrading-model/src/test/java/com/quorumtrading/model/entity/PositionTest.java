package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.Money;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class PositionTest {

    @Test
    void shouldCreateEmptyPosition() {
        Symbol symbol = new Symbol("BTC", InvestmentType.CRYPTO);

        Position position = Position.empty(symbol);

        assertThat(position.symbol()).isEqualTo(symbol);
        assertThat(position.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(position.averagePrice().amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldAddQuantityWithWeightedAverageCost() {
        Symbol symbol = new Symbol("BTC", InvestmentType.CRYPTO);
        Money initialPrice = new Money(new BigDecimal("50000"), Money.Currency.USD);
        Position position = new Position(symbol, new BigDecimal("1.0"), initialPrice);

        // Buy 0.5 BTC at $60000 (weighted average should be $53333.33)
        Money newPrice = new Money(new BigDecimal("60000"), Money.Currency.USD);
        Position updated = position.addQuantity(new BigDecimal("0.5"), newPrice);

        assertThat(updated.quantity()).isEqualByComparingTo("1.5");
        // Total cost: (1.0 * 50000) + (0.5 * 60000) = 80000
        // Average: 80000 / 1.5 = 53333.33333333 (Money uses 8 decimal places)
        assertThat(updated.averagePrice().amount()).isEqualByComparingTo("53333.33333333");
    }

    @Test
    void shouldReduceQuantity() {
        Symbol symbol = new Symbol("ETH", InvestmentType.CRYPTO);
        Money price = new Money(new BigDecimal("3000"), Money.Currency.USD);
        Position position = new Position(symbol, new BigDecimal("2.0"), price);

        Position updated = position.reduceQuantity(new BigDecimal("0.5"));

        assertThat(updated.quantity()).isEqualByComparingTo("1.5");
        assertThat(updated.averagePrice()).isEqualTo(price);  // Average price unchanged
    }

    @Test
    void shouldCalculateCurrentValue() {
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);
        Money averagePrice = new Money(new BigDecimal("150"), Money.Currency.USD);
        Position position = new Position(symbol, new BigDecimal("10"), averagePrice);

        Money currentPrice = new Money(new BigDecimal("200"), Money.Currency.USD);
        Money currentValue = position.calculateValue(currentPrice);

        assertThat(currentValue.amount()).isEqualByComparingTo("2000");  // 10 * 200
    }

    @Test
    void shouldRejectReducingMoreThanAvailable() {
        Symbol symbol = new Symbol("BTC", InvestmentType.CRYPTO);
        Money price = new Money(new BigDecimal("50000"), Money.Currency.USD);
        Position position = new Position(symbol, new BigDecimal("1.0"), price);

        assertThatThrownBy(() -> position.reduceQuantity(new BigDecimal("1.5")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot reduce quantity");
    }
}
