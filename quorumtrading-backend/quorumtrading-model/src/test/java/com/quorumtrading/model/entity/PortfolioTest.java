package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class PortfolioTest {

    @Test
    void shouldCreateEmptyPortfolio() {
        SimulationId simId = SimulationId.generate();
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Portfolio portfolio = new Portfolio(simId, new HashMap<>(), initialCash);

        assertThat(portfolio.simulationId()).isEqualTo(simId);
        assertThat(portfolio.cashAvailable()).isEqualTo(initialCash);
        assertThat(portfolio.positions()).isEmpty();
    }

    @Test
    void shouldExecuteBuyTrade() {
        SimulationId simId = SimulationId.generate();
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);
        Portfolio portfolio = new Portfolio(simId, new HashMap<>(), initialCash);

        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        Money btcPrice = new Money(new BigDecimal("50000"), Money.Currency.USD);
        BigDecimal quantity = new BigDecimal("1.5");

        Portfolio updated = portfolio.buy(btc, quantity, btcPrice);

        // Cash reduced by 1.5 * 50000 = 75000
        assertThat(updated.cashAvailable().amount()).isEqualByComparingTo("25000");

        // Position created
        assertThat(updated.positions()).containsKey(btc);
        assertThat(updated.positions().get(btc).quantity()).isEqualByComparingTo("1.5");
        assertThat(updated.positions().get(btc).averagePrice()).isEqualTo(btcPrice);
    }

    @Test
    void shouldExecuteSellTrade() {
        SimulationId simId = SimulationId.generate();
        Money initialCash = new Money(new BigDecimal("10000"), Money.Currency.USD);

        // Start with 2 BTC position
        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        Money buyPrice = new Money(new BigDecimal("50000"), Money.Currency.USD);
        Position position = new Position(btc, new BigDecimal("2.0"), buyPrice);
        Map<Symbol, Position> positions = new HashMap<>();
        positions.put(btc, position);

        Portfolio portfolio = new Portfolio(simId, positions, initialCash);

        Money sellPrice = new Money(new BigDecimal("60000"), Money.Currency.USD);
        Portfolio updated = portfolio.sell(btc, new BigDecimal("1.0"), sellPrice);

        // Cash increased by 1.0 * 60000 = 60000
        assertThat(updated.cashAvailable().amount()).isEqualByComparingTo("70000");

        // Position reduced to 1.0 BTC
        assertThat(updated.positions().get(btc).quantity()).isEqualByComparingTo("1.0");
    }

    @Test
    void shouldCalculateTotalValue() {
        SimulationId simId = SimulationId.generate();
        Money cash = new Money(new BigDecimal("10000"), Money.Currency.USD);

        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        Symbol eth = new Symbol("ETH", InvestmentType.CRYPTO);

        Map<Symbol, Position> positions = new HashMap<>();
        positions.put(btc, new Position(btc, new BigDecimal("1.0"),
            new Money(new BigDecimal("50000"), Money.Currency.USD)));
        positions.put(eth, new Position(eth, new BigDecimal("5.0"),
            new Money(new BigDecimal("3000"), Money.Currency.USD)));

        Portfolio portfolio = new Portfolio(simId, positions, cash);

        Map<Symbol, Money> currentPrices = new HashMap<>();
        currentPrices.put(btc, new Money(new BigDecimal("55000"), Money.Currency.USD));
        currentPrices.put(eth, new Money(new BigDecimal("3500"), Money.Currency.USD));

        Money totalValue = portfolio.calculateTotalValue(currentPrices);

        // Total: 10000 (cash) + 55000 (1 BTC) + 17500 (5 ETH) = 82500
        assertThat(totalValue.amount()).isEqualByComparingTo("82500");
    }

    @Test
    void shouldRejectBuyWithInsufficientFunds() {
        SimulationId simId = SimulationId.generate();
        Money smallCash = new Money(new BigDecimal("1000"), Money.Currency.USD);
        Portfolio portfolio = new Portfolio(simId, new HashMap<>(), smallCash);

        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        Money btcPrice = new Money(new BigDecimal("50000"), Money.Currency.USD);

        assertThatThrownBy(() -> portfolio.buy(btc, new BigDecimal("1.0"), btcPrice))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Insufficient funds");
    }

    @Test
    void shouldRejectSellWithoutPosition() {
        SimulationId simId = SimulationId.generate();
        Portfolio portfolio = new Portfolio(simId, new HashMap<>(), Money.ZERO);

        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        Money price = new Money(new BigDecimal("50000"), Money.Currency.USD);

        assertThatThrownBy(() -> portfolio.sell(btc, new BigDecimal("1.0"), price))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No position");
    }

    @Test
    void shouldPreventExternalMutationOfPositions() {
        SimulationId simId = SimulationId.generate();
        Money cash = new Money(new BigDecimal("100000"), Money.Currency.USD);
        Portfolio portfolio = new Portfolio(simId, new HashMap<>(), cash);

        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        Money price = new Money(new BigDecimal("50000"), Money.Currency.USD);
        Position position = new Position(btc, BigDecimal.ONE, price);

        // Attempt to mutate internal positions map should fail
        assertThatThrownBy(() -> portfolio.positions().put(btc, position))
            .isInstanceOf(UnsupportedOperationException.class);

        // Verify portfolio is unchanged
        assertThat(portfolio.positions()).isEmpty();
    }
}
