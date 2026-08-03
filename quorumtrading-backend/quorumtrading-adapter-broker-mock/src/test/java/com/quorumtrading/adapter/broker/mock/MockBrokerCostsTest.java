package com.quorumtrading.adapter.broker.mock;

import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.TradeAction;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import com.quorumtrading.model.enums.TradeStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Test realistic transaction costs (slippage + commission) in MockBrokerAdapter.
 * Verifies asset-type-aware commission: 0% for stocks, 0.25% for crypto (Alpaca-aligned).
 */
class MockBrokerCostsTest {

    private MockBrokerAdapter broker;
    private MarketEventRepository marketEventRepository;
    private SimulationId simulationId;
    private Symbol btcSymbol;
    private Symbol aaplSymbol;
    private Instant timestamp;

    @BeforeEach
    void setUp() {
        broker = new MockBrokerAdapter();
        marketEventRepository = Mockito.mock(MarketEventRepository.class);
        broker.marketEventRepository = marketEventRepository;

        // Enable costs with Alpaca-aligned defaults
        broker.costsEnabled = true;
        broker.commissionRate = 0.0; // 0% for stocks
        broker.cryptoCommissionRate = 0.0025; // 0.25% for crypto
        broker.baseSlippageBps = 0.0005; // 0.05%
        broker.impactPerUnit = 0.00001;

        simulationId = SimulationId.generate();
        btcSymbol = new Symbol("BTC", InvestmentType.CRYPTO);
        aaplSymbol = new Symbol("AAPL", InvestmentType.STOCK);
        timestamp = Instant.now();

        // Initialize portfolio with $100,000 (enough for both stock and crypto tests)
        broker.initializePortfolio(simulationId, Money.usd(new BigDecimal("100000.00")));
    }

    @Test
    void buyOrder_cryptoAppliesSlippageAndCommission() {
        // Given: BTC market price is $50,000
        BigDecimal marketPrice = new BigDecimal("50000.00");
        mockMarketPrice(btcSymbol, marketPrice, timestamp);

        // When: Buy 0.1 BTC
        BigDecimal quantity = new BigDecimal("0.1");
        ExecuteTradeCommand command = ExecuteTradeCommand.market(
            simulationId,
            TradeAction.BUY,
            btcSymbol,
            quantity,
            timestamp
        );

        TradeResult result = broker.executeTrade(command);

        // Then: Execution price should be HIGHER than market price (due to slippage)
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.executionPrice().amount())
            .isGreaterThan(marketPrice)
            .withFailMessage("Execution price should include slippage above market price");

        // Calculate expected costs with 0.25% crypto commission
        Money executionPrice = result.executionPrice();
        Money tradeCost = executionPrice.multiply(quantity);
        Money commission = tradeCost.multiply(BigDecimal.valueOf(0.0025)); // 0.25% crypto
        Money totalCost = tradeCost.add(commission);

        // Verify portfolio deduction includes commission
        Money remainingCash = broker.getAvailableBalance(simulationId);
        Money expectedCash = Money.usd(new BigDecimal("100000.00")).subtract(totalCost);

        assertThat(remainingCash.amount().setScale(2, RoundingMode.HALF_UP))
            .isEqualByComparingTo(expectedCash.amount().setScale(2, RoundingMode.HALF_UP))
            .withFailMessage("Cash should be deducted by trade cost + 0.25%% crypto commission");
    }

    @Test
    void buyOrder_stockHasZeroCommission() {
        // Given: AAPL market price is $200
        BigDecimal marketPrice = new BigDecimal("200.00");
        mockMarketPrice(aaplSymbol, marketPrice, timestamp);

        Money cashBefore = broker.getAvailableBalance(simulationId);

        // When: Buy 10 shares of AAPL
        BigDecimal quantity = new BigDecimal("10");
        ExecuteTradeCommand command = ExecuteTradeCommand.market(
            simulationId,
            TradeAction.BUY,
            aaplSymbol,
            quantity,
            timestamp
        );

        TradeResult result = broker.executeTrade(command);

        // Then: Trade succeeds
        assertThat(result.isSuccessful()).isTrue();

        // Calculate: with 0% stock commission, total cost = trade cost only (+ slippage)
        Money executionPrice = result.executionPrice();
        Money tradeCost = executionPrice.multiply(quantity);
        // Commission should be $0 for stocks
        Money expectedCash = cashBefore.subtract(tradeCost);

        Money remainingCash = broker.getAvailableBalance(simulationId);
        assertThat(remainingCash.amount().setScale(2, RoundingMode.HALF_UP))
            .isEqualByComparingTo(expectedCash.amount().setScale(2, RoundingMode.HALF_UP))
            .withFailMessage("Stock trade should have zero commission (only slippage)");
    }

    @Test
    void sellOrder_cryptoAppliesSlippageAndCommission() {
        // Given: Buy 0.1 BTC first at $50,000
        BigDecimal marketPrice = new BigDecimal("50000.00");
        mockMarketPrice(btcSymbol, marketPrice, timestamp);

        ExecuteTradeCommand buyCommand = ExecuteTradeCommand.market(
            simulationId,
            TradeAction.BUY,
            btcSymbol,
            new BigDecimal("0.1"),
            timestamp
        );
        broker.executeTrade(buyCommand);

        // When: Sell 0.1 BTC (price still $50,000)
        Instant sellTimestamp = timestamp.plusSeconds(3600);
        mockMarketPrice(btcSymbol, marketPrice, sellTimestamp);

        ExecuteTradeCommand sellCommand = ExecuteTradeCommand.market(
            simulationId,
            TradeAction.SELL,
            btcSymbol,
            new BigDecimal("0.1"),
            sellTimestamp
        );

        TradeResult result = broker.executeTrade(sellCommand);

        // Then: Execution price should be LOWER than market price (due to slippage)
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.executionPrice().amount())
            .isLessThan(marketPrice)
            .withFailMessage("Execution price should include slippage below market price");
    }

    @Test
    void sellOrder_stockHasZeroCommission() {
        // Given: Buy 10 AAPL first at $200
        BigDecimal marketPrice = new BigDecimal("200.00");
        mockMarketPrice(aaplSymbol, marketPrice, timestamp);

        ExecuteTradeCommand buyCommand = ExecuteTradeCommand.market(
            simulationId,
            TradeAction.BUY,
            aaplSymbol,
            new BigDecimal("10"),
            timestamp
        );
        broker.executeTrade(buyCommand);
        Money cashAfterBuy = broker.getAvailableBalance(simulationId);

        // When: Sell 10 AAPL (same price)
        Instant sellTimestamp = timestamp.plusSeconds(3600);
        mockMarketPrice(aaplSymbol, marketPrice, sellTimestamp);

        ExecuteTradeCommand sellCommand = ExecuteTradeCommand.market(
            simulationId,
            TradeAction.SELL,
            aaplSymbol,
            new BigDecimal("10"),
            sellTimestamp
        );
        TradeResult result = broker.executeTrade(sellCommand);

        // Then: Net proceeds = execution price * qty (no commission)
        assertThat(result.isSuccessful()).isTrue();
        Money executionPrice = result.executionPrice();
        Money grossProceeds = executionPrice.multiply(new BigDecimal("10"));
        // With 0% commission, net proceeds = gross proceeds
        Money expectedCash = cashAfterBuy.add(grossProceeds);

        Money remainingCash = broker.getAvailableBalance(simulationId);
        assertThat(remainingCash.amount().setScale(2, RoundingMode.HALF_UP))
            .isEqualByComparingTo(expectedCash.amount().setScale(2, RoundingMode.HALF_UP))
            .withFailMessage("Stock sell should have zero commission");
    }

    @Test
    void roundTripTrade_cryptoIncursMoreLossThanStock() {
        // Compare round-trip costs: crypto (0.25% commission) vs stock (0% commission)
        BigDecimal marketPrice = new BigDecimal("1000.00");

        // Round-trip crypto trade
        SimulationId cryptoSim = SimulationId.generate();
        broker.initializePortfolio(cryptoSim, Money.usd(new BigDecimal("100000.00")));
        Symbol cryptoSymbol = new Symbol("ETH", InvestmentType.CRYPTO);

        mockMarketPrice(cryptoSymbol, marketPrice, timestamp);
        broker.executeTrade(ExecuteTradeCommand.market(cryptoSim, TradeAction.BUY, cryptoSymbol, new BigDecimal("10"), timestamp));
        Instant sellTime = timestamp.plusSeconds(1);
        mockMarketPrice(cryptoSymbol, marketPrice, sellTime);
        broker.executeTrade(ExecuteTradeCommand.market(cryptoSim, TradeAction.SELL, cryptoSymbol, new BigDecimal("10"), sellTime));
        Money cryptoFinalCash = broker.getAvailableBalance(cryptoSim);

        // Round-trip stock trade
        SimulationId stockSim = SimulationId.generate();
        broker.initializePortfolio(stockSim, Money.usd(new BigDecimal("100000.00")));
        Symbol stockSymbol = new Symbol("MSFT", InvestmentType.STOCK);

        mockMarketPrice(stockSymbol, marketPrice, timestamp);
        broker.executeTrade(ExecuteTradeCommand.market(stockSim, TradeAction.BUY, stockSymbol, new BigDecimal("10"), timestamp));
        mockMarketPrice(stockSymbol, marketPrice, sellTime);
        broker.executeTrade(ExecuteTradeCommand.market(stockSim, TradeAction.SELL, stockSymbol, new BigDecimal("10"), sellTime));
        Money stockFinalCash = broker.getAvailableBalance(stockSim);

        // Crypto should lose more than stock due to 0.25% commission on both buy and sell
        assertThat(cryptoFinalCash.amount()).isLessThan(stockFinalCash.amount())
            .withFailMessage("Crypto round-trip should cost more than stock due to commission");

        BigDecimal initial = new BigDecimal("100000.00");
        BigDecimal cryptoLoss = initial.subtract(cryptoFinalCash.amount());
        BigDecimal stockLoss = initial.subtract(stockFinalCash.amount());

        // Stock loss should only be slippage (no commission)
        // Crypto loss should be slippage + ~0.5% commission (0.25% buy + 0.25% sell)
        assertThat(cryptoLoss.subtract(stockLoss).doubleValue())
            .isGreaterThan(0)
            .withFailMessage("Extra crypto cost should be positive");
    }

    @Test
    void costsDisabled_noSlippageOrCommission() {
        // Given: Costs disabled
        broker.costsEnabled = false;

        BigDecimal marketPrice = new BigDecimal("50000.00");
        mockMarketPrice(btcSymbol, marketPrice, timestamp);

        // When: Buy 0.1 BTC
        ExecuteTradeCommand command = ExecuteTradeCommand.market(
            simulationId,
            TradeAction.BUY,
            btcSymbol,
            new BigDecimal("0.1"),
            timestamp
        );

        TradeResult result = broker.executeTrade(command);

        // Then: Execution price equals market price (no slippage)
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.executionPrice().amount())
            .isEqualByComparingTo(marketPrice)
            .withFailMessage("When costs disabled, execution price should equal market price");

        // And: Cash deducted equals exact trade value (no commission)
        Money expectedCashDeduction = Money.usd(marketPrice).multiply(new BigDecimal("0.1"));
        Money remainingCash = broker.getAvailableBalance(simulationId);
        Money actualDeduction = Money.usd(new BigDecimal("100000.00")).subtract(remainingCash);

        assertThat(actualDeduction.amount().setScale(2, RoundingMode.HALF_UP))
            .isEqualByComparingTo(expectedCashDeduction.amount().setScale(2, RoundingMode.HALF_UP))
            .withFailMessage("When costs disabled, cash deduction should equal exact trade value");
    }

    // ------------------------------------------------------------------
    // Market impact must scale with the ORDER'S VALUE, not its unit count.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a sub-cent asset fills near its market price, not at a multiple of it")
    void marketImpactDoesNotExplodeForLowPricedAssets() {
        // Impact used to be quantity * impactPerUnit, reaching 1% at 1,000 units whatever
        // the unit price. A $5,000 order in a $0.072 coin is ~69,000 units, so it filled
        // at $0.1147 against a $0.0721 market — a 59% invented cost — and a three-day
        // backtest reported -28.99% on an asset that moved -0.98%.
        Symbol doge = new Symbol("DOGE", InvestmentType.CRYPTO);
        BigDecimal marketPrice = new BigDecimal("0.07212955");
        mockMarketPrice(doge, marketPrice, timestamp);

        BigDecimal quantity = new BigDecimal("69000");   // ≈ $4,977 notional
        TradeResult result = broker.executeTrade(ExecuteTradeCommand.market(
            simulationId, TradeAction.BUY, doge, quantity, timestamp));

        assertThat(result.status()).isEqualTo(TradeStatus.EXECUTED);
        BigDecimal premium = result.executionPrice().amount()
            .subtract(marketPrice)
            .divide(marketPrice, 6, java.math.RoundingMode.HALF_UP);
        assertThat(premium)
            .as("fill premium over market must stay within a plausible slippage band")
            .isLessThan(new BigDecimal("0.02"));
    }

    @Test
    @DisplayName("a micro-priced asset is buyable at all")
    void microPricedAssetIsTradable() {
        // SHIB demanded $67,150,857 for a $5,000 order, so every BUY was rejected and the
        // run finished at 0.00% — indistinguishable from a strategy that never triggered.
        Symbol shib = new Symbol("SHIB", InvestmentType.CRYPTO);
        BigDecimal marketPrice = new BigDecimal("0.00000461");
        mockMarketPrice(shib, marketPrice, timestamp);

        BigDecimal quantity = new BigDecimal("1000000000");  // ≈ $4,610 notional
        TradeResult result = broker.executeTrade(ExecuteTradeCommand.market(
            simulationId, TradeAction.BUY, shib, quantity, timestamp));

        assertThat(result.status())
            .as("a $4,610 order inside a $100,000 portfolio must not be rejected")
            .isEqualTo(TradeStatus.EXECUTED);
    }

    @Test
    @DisplayName("equal notional in a cheap and an expensive asset costs the same impact")
    void impactDependsOnNotionalNotUnitPrice() {
        Symbol cheap = new Symbol("CHEAP", InvestmentType.STOCK);
        Symbol dear = new Symbol("DEAR", InvestmentType.STOCK);
        BigDecimal cheapPrice = new BigDecimal("1.00");
        BigDecimal dearPrice = new BigDecimal("1000.00");

        // The price mock matches any ticker, so the two must be staged in turn rather
        // than side by side — stubbing both leaves only the second in force.
        mockMarketPrice(cheap, cheapPrice, timestamp);
        TradeResult cheapFill = broker.executeTrade(ExecuteTradeCommand.market(
            simulationId, TradeAction.BUY, cheap, new BigDecimal("10000"), timestamp));

        mockMarketPrice(dear, dearPrice, timestamp);
        TradeResult dearFill = broker.executeTrade(ExecuteTradeCommand.market(
            simulationId, TradeAction.BUY, dear, new BigDecimal("10"), timestamp));

        assertThat(cheapFill.status()).isEqualTo(TradeStatus.EXECUTED);
        assertThat(dearFill.status()).isEqualTo(TradeStatus.EXECUTED);

        BigDecimal cheapPremium = cheapFill.executionPrice().amount()
            .subtract(cheapPrice).divide(cheapPrice, 6, java.math.RoundingMode.HALF_UP);
        BigDecimal dearPremium = dearFill.executionPrice().amount()
            .subtract(dearPrice).divide(dearPrice, 6, java.math.RoundingMode.HALF_UP);

        // Both carry the same base slippage and the same notional-scaled impact; only the
        // +/-20% noise separates them, so they must stay within a factor of two.
        assertThat(cheapPremium.doubleValue())
            .as("a dollar stock and a thousand-dollar stock must pay comparably for the "
                + "same money at risk")
            .isBetween(dearPremium.doubleValue() / 2.5, dearPremium.doubleValue() * 2.5);
    }

    private void mockMarketPrice(Symbol symbol, BigDecimal price, Instant timestamp) {
        MarketEvent priceEvent = new MarketEvent(
            null,  // id
            timestamp,  // timestamp
            EventType.PRICE,  // eventType
            "mock",  // source
            symbol.ticker(),  // symbol
            Map.of("price", price),  // rawData
            timestamp,  // ingestedAt
            null,  // cachedAt
            null  // contentHash
        );

        when(marketEventRepository.findByTickersAndTimeRange(
            anySet(),
            anySet(),
            any(Instant.class),
            any(Instant.class),
            eq(EventType.PRICE),
            anyInt()
        )).thenReturn(List.of(priceEvent));
    }
}
