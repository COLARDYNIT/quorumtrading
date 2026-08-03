package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimulationPortfolioTest {

    @Test
    void shouldInitializeWithTradingLimit() {
        SimulationId simId = SimulationId.generate();
        BrokerAccountId accountId = BrokerAccountId.generate();
        Money tradingLimit = new Money(new BigDecimal("20000"), Money.Currency.USD);

        SimulationPortfolio portfolio = SimulationPortfolio.initialize(simId, accountId, tradingLimit);

        assertEquals(tradingLimit, portfolio.getCashAvailable());
        assertEquals(tradingLimit, portfolio.getCashReserved());
        assertTrue(portfolio.getPositions().isEmpty());
        assertEquals(Money.zero(Money.Currency.USD), portfolio.getRealizedPnL());
    }

    @Test
    void shouldRecordBuyTrade() {
        SimulationPortfolio portfolio = createTestPortfolio(new BigDecimal("10000"));
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);
        BigDecimal quantity = new BigDecimal("10");
        Money price = new Money(new BigDecimal("150"), Money.Currency.USD);
        Money totalCost = new Money(new BigDecimal("1500"), Money.Currency.USD);

        portfolio.recordBuy(symbol, quantity, price, totalCost);

        assertEquals(1, portfolio.getPositions().size());
        Position position = portfolio.getPositions().get(symbol);
        assertEquals(quantity, position.quantity());
        assertEquals(new Money(new BigDecimal("8500"), Money.Currency.USD), portfolio.getCashAvailable());
    }

    @Test
    void shouldRecordSellTradeAndCalculatePnL() {
        SimulationPortfolio portfolio = createTestPortfolio(new BigDecimal("10000"));
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);

        // Buy at $100
        portfolio.recordBuy(symbol, new BigDecimal("10"),
            new Money(new BigDecimal("100"), Money.Currency.USD),
            new Money(new BigDecimal("1000"), Money.Currency.USD));

        // Sell at $150 (profit of $500)
        Money sellPrice = new Money(new BigDecimal("150"), Money.Currency.USD);
        Money proceeds = new Money(new BigDecimal("1500"), Money.Currency.USD);
        portfolio.recordSell(symbol, new BigDecimal("10"), sellPrice, proceeds);

        assertEquals(new Money(new BigDecimal("500"), Money.Currency.USD), portfolio.getRealizedPnL());
        assertTrue(portfolio.getPositions().isEmpty());
    }

    @Test
    void shouldCalculateUnrealizedPnL() {
        SimulationPortfolio portfolio = createTestPortfolio(new BigDecimal("10000"));
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);

        // Buy 10 shares at $100 each (cost basis = $1000)
        portfolio.recordBuy(symbol, new BigDecimal("10"),
            new Money(new BigDecimal("100"), Money.Currency.USD),
            new Money(new BigDecimal("1000"), Money.Currency.USD));

        // Current price is $150, so unrealized P&L = (150 - 100) * 10 = $500
        Map<Symbol, Money> currentPrices = Map.of(
            symbol, new Money(new BigDecimal("150"), Money.Currency.USD)
        );

        Money unrealizedPnL = portfolio.calculateUnrealizedPnL(currentPrices);
        assertEquals(new Money(new BigDecimal("500"), Money.Currency.USD), unrealizedPnL);
    }

    @Test
    void shouldCalculateTotalValue() {
        SimulationPortfolio portfolio = createTestPortfolio(new BigDecimal("10000"));
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);

        // Buy 10 shares at $100 each (cost = $1000, remaining cash = $9000)
        portfolio.recordBuy(symbol, new BigDecimal("10"),
            new Money(new BigDecimal("100"), Money.Currency.USD),
            new Money(new BigDecimal("1000"), Money.Currency.USD));

        // Current price is $150, so position value = $1500
        // Total value = $9000 (cash) + $1500 (position) = $10500
        Map<Symbol, Money> currentPrices = Map.of(
            symbol, new Money(new BigDecimal("150"), Money.Currency.USD)
        );

        Money totalValue = portfolio.calculateTotalValue(currentPrices);
        assertEquals(new Money(new BigDecimal("10500"), Money.Currency.USD), totalValue);
    }

    @Test
    void shouldAddToExistingPositionOnBuy() {
        SimulationPortfolio portfolio = createTestPortfolio(new BigDecimal("10000"));
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);

        // Buy 10 shares at $100
        portfolio.recordBuy(symbol, new BigDecimal("10"),
            new Money(new BigDecimal("100"), Money.Currency.USD),
            new Money(new BigDecimal("1000"), Money.Currency.USD));

        // Buy 10 more shares at $200
        portfolio.recordBuy(symbol, new BigDecimal("10"),
            new Money(new BigDecimal("200"), Money.Currency.USD),
            new Money(new BigDecimal("2000"), Money.Currency.USD));

        Position position = portfolio.getPositions().get(symbol);
        assertEquals(new BigDecimal("20"), position.quantity());
        // Average price: (10 * 100 + 10 * 200) / 20 = 3000 / 20 = 150
        assertEquals(0, position.averagePrice().amount().compareTo(new BigDecimal("150")));
    }

    @Test
    void shouldCalculateLossOnSell() {
        SimulationPortfolio portfolio = createTestPortfolio(new BigDecimal("10000"));
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);

        // Buy at $100
        portfolio.recordBuy(symbol, new BigDecimal("10"),
            new Money(new BigDecimal("100"), Money.Currency.USD),
            new Money(new BigDecimal("1000"), Money.Currency.USD));

        // Sell at $80 (loss of $200)
        Money sellPrice = new Money(new BigDecimal("80"), Money.Currency.USD);
        Money proceeds = new Money(new BigDecimal("800"), Money.Currency.USD);
        portfolio.recordSell(symbol, new BigDecimal("10"), sellPrice, proceeds);

        assertEquals(new Money(new BigDecimal("-200"), Money.Currency.USD), portfolio.getRealizedPnL());
    }

    @Test
    void shouldPartialSellAndUpdatePosition() {
        SimulationPortfolio portfolio = createTestPortfolio(new BigDecimal("10000"));
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);

        // Buy 20 shares at $100
        portfolio.recordBuy(symbol, new BigDecimal("20"),
            new Money(new BigDecimal("100"), Money.Currency.USD),
            new Money(new BigDecimal("2000"), Money.Currency.USD));

        // Sell 10 shares at $150 (profit of $500)
        portfolio.recordSell(symbol, new BigDecimal("10"),
            new Money(new BigDecimal("150"), Money.Currency.USD),
            new Money(new BigDecimal("1500"), Money.Currency.USD));

        // 10 shares remaining
        Position position = portfolio.getPositions().get(symbol);
        assertEquals(0, position.quantity().compareTo(new BigDecimal("10")));
        assertEquals(new Money(new BigDecimal("500"), Money.Currency.USD), portfolio.getRealizedPnL());
    }

    @Test
    void shouldRejectSellWithNoPosition() {
        SimulationPortfolio portfolio = createTestPortfolio(new BigDecimal("10000"));
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);

        assertThrows(IllegalStateException.class, () ->
            portfolio.recordSell(symbol, new BigDecimal("10"),
                new Money(new BigDecimal("150"), Money.Currency.USD),
                new Money(new BigDecimal("1500"), Money.Currency.USD)));
    }

    @Test
    void shouldRejectSellMoreThanOwned() {
        SimulationPortfolio portfolio = createTestPortfolio(new BigDecimal("10000"));
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);

        // Buy 10 shares
        portfolio.recordBuy(symbol, new BigDecimal("10"),
            new Money(new BigDecimal("100"), Money.Currency.USD),
            new Money(new BigDecimal("1000"), Money.Currency.USD));

        // Try to sell 20 shares
        assertThrows(IllegalArgumentException.class, () ->
            portfolio.recordSell(symbol, new BigDecimal("20"),
                new Money(new BigDecimal("150"), Money.Currency.USD),
                new Money(new BigDecimal("3000"), Money.Currency.USD)));
    }

    @Test
    void shouldRejectBuyWithInsufficientCash() {
        SimulationPortfolio portfolio = createTestPortfolio(new BigDecimal("1000"));
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);

        // Try to buy $2000 worth with only $1000 available
        assertThrows(IllegalStateException.class, () ->
            portfolio.recordBuy(symbol, new BigDecimal("20"),
                new Money(new BigDecimal("100"), Money.Currency.USD),
                new Money(new BigDecimal("2000"), Money.Currency.USD)));
    }

    @Test
    void shouldReconstructFromPersistence() {
        SimulationId simId = SimulationId.generate();
        BrokerAccountId accountId = BrokerAccountId.generate();
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);
        Position position = new Position(symbol, new BigDecimal("10"),
            new Money(new BigDecimal("100"), Money.Currency.USD));
        Map<Symbol, Position> positions = Map.of(symbol, position);
        Money cashAvailable = new Money(new BigDecimal("9000"), Money.Currency.USD);
        Money cashReserved = new Money(new BigDecimal("10000"), Money.Currency.USD);
        Money realizedPnL = new Money(new BigDecimal("500"), Money.Currency.USD);

        SimulationPortfolio portfolio = new SimulationPortfolio(
            simId, accountId, positions, cashAvailable, cashReserved, realizedPnL);

        assertEquals(simId, portfolio.getSimulationId());
        assertEquals(accountId, portfolio.getBrokerAccountId());
        assertEquals(1, portfolio.getPositions().size());
        assertEquals(cashAvailable, portfolio.getCashAvailable());
        assertEquals(cashReserved, portfolio.getCashReserved());
        assertEquals(realizedPnL, portfolio.getRealizedPnL());
    }

    @Test
    void shouldReturnUnmodifiablePositionsMap() {
        SimulationPortfolio portfolio = createTestPortfolio(new BigDecimal("10000"));
        Symbol symbol = new Symbol("AAPL", InvestmentType.STOCK);

        portfolio.recordBuy(symbol, new BigDecimal("10"),
            new Money(new BigDecimal("100"), Money.Currency.USD),
            new Money(new BigDecimal("1000"), Money.Currency.USD));

        Map<Symbol, Position> positions = portfolio.getPositions();
        Symbol newSymbol = new Symbol("TSLA", InvestmentType.STOCK);
        Position newPosition = new Position(newSymbol, BigDecimal.ONE, Money.ZERO_USD);

        assertThrows(UnsupportedOperationException.class, () ->
            positions.put(newSymbol, newPosition));
    }

    private SimulationPortfolio createTestPortfolio(BigDecimal amount) {
        return SimulationPortfolio.initialize(
            SimulationId.generate(),
            BrokerAccountId.generate(),
            new Money(amount, Money.Currency.USD)
        );
    }
}
