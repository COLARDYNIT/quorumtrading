package com.quorumtrading.adapter.broker.mock;

import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.TradeAction;
import com.quorumtrading.model.enums.TradeStatus;
import com.quorumtrading.model.enums.CostPreset;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.MockBrokerConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockBrokerAdapterTest {

    @Mock
    MarketEventRepository marketEventRepository;

    @InjectMocks
    MockBrokerAdapter broker;

    private SimulationId simulationId;
    private Symbol btcSymbol;
    private Symbol aaplSymbol;
    private Instant timestamp;

    @BeforeEach
    void setUp() {
        simulationId = SimulationId.generate();
        btcSymbol = new Symbol("BTC", InvestmentType.CRYPTO);
        aaplSymbol = new Symbol("AAPL", InvestmentType.STOCK);
        timestamp = Instant.now();
    }

    /**
     * Two simulations on one mock account must not share a pot of simulated cash.
     *
     * <p>Portfolio state used to be keyed by broker account, so both simulations resolved to a
     * single state. A $500 plan-funded simulation then reported $10,000 and +900% because an
     * unrelated $10,000 backtest on the same account had written that state. Only a real broker
     * holds genuinely shared account state, because there the money really is shared.</p>
     */
    @Test
    void twoSimulationsOnOneMockAccountKeepSeparatePortfolios() {
        SimulationId other = SimulationId.generate();
        BrokerAccountId accountId = BrokerAccountId.generate();
        broker.configureAccount(accountId,
            MockBrokerConfig.fromPreset(Money.usd(1000), CostPreset.ZERO_FEES));
        broker.linkSimulationToAccount(simulationId, accountId);
        broker.linkSimulationToAccount(other, accountId);

        assertThat(broker.getAvailableBalance(simulationId)).isEqualTo(Money.usd(1000));
        assertThat(broker.getAvailableBalance(other)).isEqualTo(Money.usd(1000));

        mockMarketPrice(aaplSymbol, Money.usd(100), timestamp);
        TradeResult result = broker.executeTrade(ExecuteTradeCommand.market(
            simulationId, TradeAction.BUY, aaplSymbol, new BigDecimal("5"), timestamp));
        assertThat(result.status()).isEqualTo(TradeStatus.EXECUTED);

        // The trading simulation spent its own cash and holds its own position...
        assertThat(broker.getAvailableBalance(simulationId).amount())
            .isEqualByComparingTo(new BigDecimal("500"));
        assertThat(broker.getCurrentPortfolio(simulationId).positions()).isNotEmpty();

        // ...while its neighbour on the same account is untouched.
        assertThat(broker.getAvailableBalance(other).amount())
            .isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(broker.getCurrentPortfolio(other).positions()).isEmpty();
    }

    /**
     * A simulation funded with its own slice must not inherit the account's whole starting capital.
     *
     * <p>Linking seeds a placeholder from the account so the simulation always has isolated state,
     * but a mock account describes the ACCOUNT's starting capital, not this simulation's share of
     * it. initializePortfolio() then found state already present and skipped, so a $250
     * plan-funded contribution against a $50,000 mock account ran with $50,000 and reported
     * +19,900% on $250 of funding.</p>
     */
    @Test
    void explicitInitialCashReplacesTheAccountSeededPlaceholder() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        broker.configureAccount(accountId,
            MockBrokerConfig.fromPreset(Money.usd(50000), CostPreset.ZERO_FEES));
        broker.linkSimulationToAccount(simulationId, accountId);

        // Seeded from the account until the simulation states its own funding.
        assertThat(broker.getAvailableBalance(simulationId)).isEqualTo(Money.usd(50000));

        broker.initializePortfolio(simulationId, Money.usd(250));

        assertThat(broker.getAvailableBalance(simulationId).amount())
            .isEqualByComparingTo(new BigDecimal("250"));
        assertThat(broker.getCurrentPortfolio(simulationId).cashAvailable().amount())
            .isEqualByComparingTo(new BigDecimal("250"));
    }

    /**
     * Only the FIRST explicit initialization replaces the seed. A later call must not reset a
     * portfolio that has since traded — that would silently refund spent cash.
     */
    @Test
    void aSecondInitializeDoesNotResetAPortfolioThatHasTraded() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        broker.configureAccount(accountId,
            MockBrokerConfig.fromPreset(Money.usd(50000), CostPreset.ZERO_FEES));
        broker.linkSimulationToAccount(simulationId, accountId);
        broker.initializePortfolio(simulationId, Money.usd(1000));

        mockMarketPrice(aaplSymbol, Money.usd(100), timestamp);
        broker.executeTrade(ExecuteTradeCommand.market(
            simulationId, TradeAction.BUY, aaplSymbol, new BigDecimal("2"), timestamp));
        assertThat(broker.getAvailableBalance(simulationId).amount())
            .isEqualByComparingTo(new BigDecimal("800"));

        broker.initializePortfolio(simulationId, Money.usd(1000));

        assertThat(broker.getAvailableBalance(simulationId).amount())
            .isEqualByComparingTo(new BigDecimal("800"));
    }

    @Test
    void shouldInitializePortfolioWithInitialCash() {
        Money initialCash = Money.usd(10000);

        broker.initializePortfolio(simulationId, initialCash);

        Money balance = broker.getAvailableBalance(simulationId);
        assertThat(balance).isEqualTo(initialCash);
    }

    @Test
    void shouldExecuteBuyOrderSuccessfully() {
        // Arrange
        Money initialCash = Money.usd(10000);
        Money btcPrice = Money.usd(50000);
        BigDecimal quantity = new BigDecimal("0.1");

        broker.initializePortfolio(simulationId, initialCash);
        mockMarketPrice(btcSymbol, btcPrice, timestamp);

        ExecuteTradeCommand command = ExecuteTradeCommand.market(
            simulationId,
            TradeAction.BUY,
            btcSymbol,
            quantity,
            timestamp
        );

        // Act
        TradeResult result = broker.executeTrade(command);

        // Assert
        assertThat(result.status()).isEqualTo(TradeStatus.EXECUTED);
        assertThat(result.executionPrice()).isEqualTo(btcPrice);
        assertThat(result.filledQuantity()).isEqualTo(quantity);

        // Verify cash was deducted
        Money expectedCash = initialCash.subtract(btcPrice.multiply(quantity));
        assertThat(broker.getAvailableBalance(simulationId)).isEqualTo(expectedCash);

        // Verify position was created
        Portfolio portfolio = broker.getCurrentPortfolio(simulationId);
        assertThat(portfolio.positions()).containsKey(btcSymbol);
        assertThat(portfolio.positions().get(btcSymbol).quantity()).isEqualTo(quantity);
    }

    @Test
    void shouldRejectBuyOrderWhenInsufficientFunds() {
        // Arrange
        Money initialCash = Money.usd(1000);
        Money btcPrice = Money.usd(50000);
        BigDecimal quantity = new BigDecimal("1.0"); // Costs 50000, but only have 1000

        broker.initializePortfolio(simulationId, initialCash);
        mockMarketPrice(btcSymbol, btcPrice, timestamp);

        ExecuteTradeCommand command = ExecuteTradeCommand.market(
            simulationId,
            TradeAction.BUY,
            btcSymbol,
            quantity,
            timestamp
        );

        // Act
        TradeResult result = broker.executeTrade(command);

        // Assert
        assertThat(result.status()).isEqualTo(TradeStatus.REJECTED);
        assertThat(result.getRejectionReason()).isPresent();
        assertThat(result.getRejectionReason().get()).contains("Insufficient funds");

        // Verify cash was not deducted
        assertThat(broker.getAvailableBalance(simulationId)).isEqualTo(initialCash);
    }

    @Test
    void shouldExecuteSellOrderSuccessfully() {
        // Arrange
        Money initialCash = Money.usd(10000);
        Money btcPrice = Money.usd(50000);
        BigDecimal quantity = new BigDecimal("0.1");

        broker.initializePortfolio(simulationId, initialCash);
        mockMarketPrice(btcSymbol, btcPrice, timestamp);

        // First buy some BTC
        ExecuteTradeCommand buyCommand = ExecuteTradeCommand.market(
            simulationId,
            TradeAction.BUY,
            btcSymbol,
            quantity,
            timestamp
        );
        broker.executeTrade(buyCommand);

        // Now sell it
        ExecuteTradeCommand sellCommand = ExecuteTradeCommand.market(
            simulationId,
            TradeAction.SELL,
            btcSymbol,
            quantity,
            timestamp
        );

        // Act
        TradeResult result = broker.executeTrade(sellCommand);

        // Assert
        assertThat(result.status()).isEqualTo(TradeStatus.EXECUTED);
        assertThat(result.executionPrice()).isEqualTo(btcPrice);
        assertThat(result.filledQuantity()).isEqualTo(quantity);

        // Verify position was closed (no positions left)
        Portfolio portfolio = broker.getCurrentPortfolio(simulationId);
        assertThat(portfolio.positions()).isEmpty();

        // Verify cash was returned (should be back to initial, ignoring scale differences)
        Money finalCash = broker.getAvailableBalance(simulationId);
        assertThat(finalCash.compareTo(initialCash)).isEqualTo(0);
    }

    @Test
    void shouldRejectSellOrderWhenNoPosition() {
        // Arrange
        Money initialCash = Money.usd(10000);
        Money btcPrice = Money.usd(50000);
        BigDecimal quantity = new BigDecimal("0.1");

        broker.initializePortfolio(simulationId, initialCash);
        mockMarketPrice(btcSymbol, btcPrice, timestamp);

        ExecuteTradeCommand command = ExecuteTradeCommand.market(
            simulationId,
            TradeAction.SELL,
            btcSymbol,
            quantity,
            timestamp
        );

        // Act
        TradeResult result = broker.executeTrade(command);

        // Assert
        assertThat(result.status()).isEqualTo(TradeStatus.REJECTED);
        assertThat(result.getRejectionReason()).isPresent();
        assertThat(result.getRejectionReason().get()).contains("No position exists");
    }

    @Test
    void shouldRejectSellOrderWhenInsufficientPosition() {
        // Arrange
        Money initialCash = Money.usd(10000);
        Money btcPrice = Money.usd(50000);
        BigDecimal buyQuantity = new BigDecimal("0.1");
        BigDecimal sellQuantity = new BigDecimal("0.5"); // Try to sell more than owned

        broker.initializePortfolio(simulationId, initialCash);
        mockMarketPrice(btcSymbol, btcPrice, timestamp);

        // Buy 0.1 BTC
        ExecuteTradeCommand buyCommand = ExecuteTradeCommand.market(
            simulationId,
            TradeAction.BUY,
            btcSymbol,
            buyQuantity,
            timestamp
        );
        broker.executeTrade(buyCommand);

        // Try to sell 0.5 BTC (more than we have)
        ExecuteTradeCommand sellCommand = ExecuteTradeCommand.market(
            simulationId,
            TradeAction.SELL,
            btcSymbol,
            sellQuantity,
            timestamp
        );

        // Act
        TradeResult result = broker.executeTrade(sellCommand);

        // Assert
        assertThat(result.status()).isEqualTo(TradeStatus.REJECTED);
        assertThat(result.getRejectionReason()).isPresent();
        assertThat(result.getRejectionReason().get()).contains("Insufficient position");
    }

    @Test
    void shouldRejectTradeWhenNoMarketDataAvailable() {
        // Arrange
        Money initialCash = Money.usd(10000);
        BigDecimal quantity = new BigDecimal("0.1");

        broker.initializePortfolio(simulationId, initialCash);

        // No market data mocked - will return empty list
        when(marketEventRepository.findByTickersAndTimeRange(
            anySet(), anySet(), any(), any(), any(), anyInt()
        )).thenReturn(List.of());

        ExecuteTradeCommand command = ExecuteTradeCommand.market(
            simulationId,
            TradeAction.BUY,
            btcSymbol,
            quantity,
            timestamp
        );

        // Act
        TradeResult result = broker.executeTrade(command);

        // Assert
        assertThat(result.status()).isEqualTo(TradeStatus.REJECTED);
        assertThat(result.getRejectionReason()).isPresent();
        assertThat(result.getRejectionReason().get()).contains("No market data available");
    }

    @Test
    void shouldClearPortfolio() {
        // Arrange
        Money initialCash = Money.usd(10000);
        broker.initializePortfolio(simulationId, initialCash);

        // Act
        broker.clearPortfolio(simulationId);

        // Assert - verifying portfolio was cleared by expecting exception
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
            () -> broker.getAvailableBalance(simulationId)
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Portfolio not initialized");
    }

    // ========== FX3: non-fractional (Saxo-flavoured) execution modeling ==========

    /**
     * Configures the broker for {@code accountId} with the given fractional behaviour
     * using the ZERO_FEES preset (no slippage, no commission) so post-trade cash and
     * position quantities are exact and easy to assert, then links the simulation and
     * seeds the account portfolio.
     */
    private BrokerAccountId configureFlavour(boolean fractionalShares, Money initialCash) {
        BrokerAccountId accountId = BrokerAccountId.generate();
        MockBrokerConfig config = MockBrokerConfig
            .fromPreset(initialCash, CostPreset.ZERO_FEES)
            .withFractionalShares(fractionalShares);
        broker.configureAccount(accountId, config);
        broker.linkSimulationToAccount(simulationId, accountId);
        broker.initializePortfolioForAccount(accountId, initialCash);
        return accountId;
    }

    @Test
    void saxoFlavouredMock_equityBuy_producesIntegralPositionAndCarriesCash() {
        // Saxo-flavoured (non-fractional) mock account with $1000 and zero fees.
        configureFlavour(false, Money.usd(1000));
        mockMarketPrice(aaplSymbol, Money.usd(150), timestamp);

        // Request a fractional 6.5 shares of AAPL @ $150 — the broker must fill whole shares.
        ExecuteTradeCommand command = ExecuteTradeCommand.market(
            simulationId, TradeAction.BUY, aaplSymbol, new BigDecimal("6.5"), timestamp);

        TradeResult result = broker.executeTrade(command);

        assertThat(result.status()).isEqualTo(TradeStatus.EXECUTED);
        // 6.5 -> 6 whole shares
        assertThat(result.filledQuantity()).isEqualByComparingTo(new BigDecimal("6"));

        Portfolio portfolio = broker.getCurrentPortfolio(simulationId);
        BigDecimal heldQty = portfolio.positions().get(aaplSymbol).quantity();
        assertThat(heldQty).isEqualByComparingTo(new BigDecimal("6"));
        // Integral: no fractional component remains.
        assertThat(heldQty.stripTrailingZeros().scale()).isLessThanOrEqualTo(0);

        // Zero fees: cost = 6 * 150 = 900, so 100 cash residue is carried.
        assertThat(broker.getAvailableBalance(simulationId).amount())
            .isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void saxoFlavouredMock_cryptoBuy_remainsFractional() {
        // Same Saxo-flavoured account, but crypto still fills fractional quantities.
        configureFlavour(false, Money.usd(10000));
        mockMarketPrice(btcSymbol, Money.usd(50000), timestamp);

        ExecuteTradeCommand command = ExecuteTradeCommand.market(
            simulationId, TradeAction.BUY, btcSymbol, new BigDecimal("0.1"), timestamp);

        TradeResult result = broker.executeTrade(command);

        assertThat(result.status()).isEqualTo(TradeStatus.EXECUTED);
        assertThat(result.filledQuantity()).isEqualByComparingTo(new BigDecimal("0.1"));
        assertThat(broker.getCurrentPortfolio(simulationId).positions().get(btcSymbol).quantity())
            .isEqualByComparingTo(new BigDecimal("0.1"));
    }

    @Test
    void saxoFlavouredMock_equityBuyBelowOneShare_isRejected() {
        // $100 buys less than one $150 share on a whole-share broker -> reject, no fill.
        configureFlavour(false, Money.usd(1000));
        mockMarketPrice(aaplSymbol, Money.usd(150), timestamp);

        ExecuteTradeCommand command = ExecuteTradeCommand.market(
            simulationId, TradeAction.BUY, aaplSymbol, new BigDecimal("0.6"), timestamp);

        TradeResult result = broker.executeTrade(command);

        assertThat(result.status()).isEqualTo(TradeStatus.REJECTED);
        assertThat(result.getRejectionReason()).isPresent();
        assertThat(result.getRejectionReason().get()).contains("whole shares");
        // No cash spent, no position opened.
        assertThat(broker.getAvailableBalance(simulationId).amount())
            .isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(broker.getCurrentPortfolio(simulationId).positions()).isEmpty();
    }

    @Test
    void alpacaFlavouredMock_equityBuy_remainsFractional_unchanged() {
        // Alpaca-flavoured (default fractional) mock account fills the fractional quantity.
        configureFlavour(true, Money.usd(1000));
        mockMarketPrice(aaplSymbol, Money.usd(150), timestamp);

        ExecuteTradeCommand command = ExecuteTradeCommand.market(
            simulationId, TradeAction.BUY, aaplSymbol, new BigDecimal("6.5"), timestamp);

        TradeResult result = broker.executeTrade(command);

        assertThat(result.status()).isEqualTo(TradeStatus.EXECUTED);
        // Fractional fill preserved: 6.5 shares.
        assertThat(result.filledQuantity()).isEqualByComparingTo(new BigDecimal("6.5"));
        assertThat(broker.getCurrentPortfolio(simulationId).positions().get(aaplSymbol).quantity())
            .isEqualByComparingTo(new BigDecimal("6.5"));
        // Zero fees: cost = 6.5 * 150 = 975, residue 25.
        assertThat(broker.getAvailableBalance(simulationId).amount())
            .isEqualByComparingTo(new BigDecimal("25"));
    }

    private void mockMarketPrice(Symbol symbol, Money price, Instant timestamp) {
        Map<String, Object> rawData = new HashMap<>();
        rawData.put("price", price.amount());

        MarketEvent priceEvent = new MarketEvent(
            timestamp,
            EventType.PRICE,
            "mock-source",
            symbol.ticker(),
            rawData
        );

        // MockBrokerAdapter passes the same ticker in both crypto and stock sets
        // because it doesn't know the asset type - it just wants the price
        when(marketEventRepository.findByTickersAndTimeRange(
            eq(Set.of(symbol.ticker())),  // Crypto tickers
            eq(Set.of(symbol.ticker())),  // Stock tickers
            any(Instant.class),
            any(Instant.class),
            eq(EventType.PRICE),
            anyInt()
        )).thenReturn(List.of(priceEvent));
    }
}
