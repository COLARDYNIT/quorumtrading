package com.quorumtrading.application.service;

import com.quorumtrading.application.config.TradingLimitsConfig;
import com.quorumtrading.application.event.PortfolioChangeEvent;
import com.quorumtrading.application.event.TradeExecutionEvent;
import com.quorumtrading.application.port.out.BrokerPort;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.EventPublisher;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.application.usecase.ExecuteConsensusDecisionUseCase.ExecutionResult;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.enums.ConsensusMethod;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.ExecutionOutcome;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.OrderId;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for ExecuteConsensusDecisionService.
 *
 * <p><b>Test Coverage:</b></p>
 * <ul>
 *   <li>HOLD decisions - no trade required</li>
 *   <li>BUY execution with cash validation</li>
 *   <li>SELL execution with holdings validation</li>
 *   <li>Minimum trade amount enforcement</li>
 *   <li>JSON action parsing</li>
 *   <li>Trade rejection handling</li>
 *   <li>Portfolio snapshot updates</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ExecuteConsensusDecisionServiceTest {

    @Mock
    private BrokerPort brokerPort;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private ConsensusDecisionRepository consensusDecisionRepository;

    @Mock
    private MarketEventRepository marketEventRepository;

    @Mock
    private EventPublisher<TradeExecutionEvent> tradeExecutionEventPublisher;

    @Mock
    private EventPublisher<PortfolioChangeEvent> portfolioChangeEventPublisher;

    @Mock
    private TradingLimitsConfig tradingLimitsConfig;

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private StrategyConfigParser strategyConfigParser;

    @Captor
    private ArgumentCaptor<ConsensusDecision> decisionCaptor;

    @Captor
    private ArgumentCaptor<TradeExecutionEvent> tradeEventCaptor;

    private ExecuteConsensusDecisionService service;
    private SimulationId simulationId;
    private Instant now;

    @BeforeEach
    void setUp() {
        service = new ExecuteConsensusDecisionService(
            brokerPort,
            portfolioRepository,
            consensusDecisionRepository,
            marketEventRepository,
            tradeExecutionEventPublisher,
            portfolioChangeEventPublisher,
            tradingLimitsConfig,
            simulationRepository,
            strategyRepository,
            strategyConfigParser
        );
        simulationId = new SimulationId(UUID.randomUUID());
        now = Instant.now();
    }

    // ========== Helper Methods ==========

    private ConsensusDecision createDecision(String finalAction) {
        ConsensusDecision decision = new ConsensusDecision(
            simulationId,
            now,
            ConsensusMethod.MAJORITY,
            com.quorumtrading.application.orchestration.agents.ConsensusActionParser.parse(finalAction)
        );
        decision.setId(1L);
        return decision;
    }

    private ConsensusDecision createDecisionWithSymbol(String finalAction, Symbol symbol, BigDecimal quantity) {
        ConsensusDecision decision = createDecision(finalAction);
        decision.setSymbol(symbol);
        decision.setQuantity(quantity);
        decision.setInvestmentType(symbol.type());
        return decision;
    }

    private MarketEvent createPriceEvent(String ticker, BigDecimal price, Instant timestamp) {
        return new MarketEvent(
            timestamp,
            EventType.PRICE,
            "test-source",
            ticker,
            Map.of("price", price.toString())
        );
    }

    private Portfolio createPortfolio(Money cash, Map<Symbol, Position> positions) {
        return new Portfolio(simulationId, positions, cash);
    }

    private PortfolioSnapshot createSnapshot(SimulationId simId, Money totalValue, Money cash) {
        return new PortfolioSnapshot(
            simId,
            now,
            Map.of(),
            totalValue,
            cash
        );
    }

    // ========== HOLD Decision Tests ==========

    @Nested
    @DisplayName("HOLD Decisions")
    class HoldDecisionTests {

        @Test
        @DisplayName("should return noTradeRequired for HOLD decision")
        void shouldReturnNoTradeRequiredForHold() {
            ConsensusDecision decision = createDecision("HOLD");
            // HOLD now creates a portfolio snapshot - mock the required dependencies
            Portfolio portfolio = createPortfolio(Money.usd(10000), Map.of());
            when(brokerPort.getCurrentPortfolio(simulationId)).thenReturn(portfolio);
            PortfolioSnapshot snapshot = createSnapshot(simulationId, Money.usd(10000), Money.usd(10000));
            snapshot.setId(42L);
            when(portfolioRepository.save(any(PortfolioSnapshot.class))).thenReturn(snapshot);

            ExecutionResult result = service.execute(decision);

            assertFalse(result.tradeExecuted());
            assertEquals("HOLD", result.action());
            assertEquals(42L, result.portfolioSnapshotId()); // HOLD now creates a portfolio snapshot
        }

        @Test
        @DisplayName("should mark decision as NO_TRADE_REQUIRED for HOLD")
        void shouldMarkDecisionAsNoTradeRequired() {
            ConsensusDecision decision = createDecision("HOLD");

            service.execute(decision);

            verify(consensusDecisionRepository).save(decisionCaptor.capture());
            ConsensusDecision saved = decisionCaptor.getValue();
            assertEquals(ExecutionOutcome.NO_TRADE_REQUIRED, saved.getExecutionOutcome());
        }

        @Test
        @DisplayName("should fire TradeExecutionEvent for HOLD decision")
        void shouldFireEventForHoldDecision() {
            ConsensusDecision decision = createDecision("HOLD");

            service.execute(decision);

            verify(tradeExecutionEventPublisher).publish(tradeEventCaptor.capture());
            TradeExecutionEvent event = tradeEventCaptor.getValue();
            assertEquals("HOLD", event.getAction());
            assertTrue(event.isSuccess());
            assertEquals(simulationId, event.getSimulationId());
        }

        @Test
        @DisplayName("should parse HOLD from JSON action")
        void shouldParseHoldFromJson() {
            ConsensusDecision decision = createDecision("{\"action\":\"HOLD\",\"symbol\":\"BTC\",\"quantity\":0}");

            ExecutionResult result = service.execute(decision);

            assertFalse(result.tradeExecuted());
            assertEquals("HOLD", result.action());
        }
    }

    // ========== SKIP Decision Tests ==========

    @Nested
    @DisplayName("SKIP Decisions")
    class SkipDecisionTests {

        @Test
        @DisplayName("should return noTradeRequired for SKIP decision")
        void shouldReturnNoTradeRequiredForSkip() {
            ConsensusDecision decision = createDecision(
                    "{\"action\":\"SKIP\",\"symbol\":\"BTC\",\"quantity\":0.0,\"confidence\":0.0,\"reasoning\":\"Orchestrator failed\"}");
            decision.setSymbol(new Symbol("BTC", InvestmentType.CRYPTO));
            decision.setQuantity(BigDecimal.ZERO);

            ExecutionResult result = service.execute(decision);

            assertFalse(result.tradeExecuted());
            assertEquals("SKIP", result.action());
        }

        @Test
        @DisplayName("should mark decision as SKIPPED, not EXECUTED")
        void shouldMarkDecisionAsSkipped() {
            ConsensusDecision decision = createDecision("SKIP");

            service.execute(decision);

            verify(consensusDecisionRepository).save(decisionCaptor.capture());
            assertEquals(ExecutionOutcome.SKIPPED, decisionCaptor.getValue().getExecutionOutcome());
        }

        @Test
        @DisplayName("should NOT execute any trade for SKIP decision")
        void shouldNotExecuteTradeForSkip() {
            ConsensusDecision decision = createDecision("SKIP");

            service.execute(decision);

            verify(brokerPort, never()).executeTrade(any());
        }

        @Test
        @DisplayName("should fire TradeExecutionEvent for SKIP decision")
        void shouldFireEventForSkipDecision() {
            ConsensusDecision decision = createDecision("SKIP");

            service.execute(decision);

            verify(tradeExecutionEventPublisher).publish(tradeEventCaptor.capture());
            TradeExecutionEvent event = tradeEventCaptor.getValue();
            assertEquals("SKIP", event.getAction());
            assertTrue(event.isSuccess());
            assertEquals(BigDecimal.ZERO, event.getQuantity());
        }
    }

    // ========== Invalid Action Tests ==========

    @Nested
    @DisplayName("Invalid Actions")
    class InvalidActionTests {

        @Test
        @DisplayName("should fail for null final action")
        void shouldFailForNullAction() {
            ConsensusDecision decision = createDecision(null);

            ExecutionResult result = service.execute(decision);

            assertFalse(result.tradeExecuted());
            assertTrue(result.message().contains("null or empty"));
        }

        @Test
        @DisplayName("should fail for empty final action")
        void shouldFailForEmptyAction() {
            ConsensusDecision decision = createDecision("");

            ExecutionResult result = service.execute(decision);

            assertFalse(result.tradeExecuted());
            assertTrue(result.message().contains("null or empty"));
        }

        @Test
        @DisplayName("should fail for invalid action type")
        void shouldFailForInvalidActionType() {
            ConsensusDecision decision = createDecision("INVALID_ACTION");

            ExecutionResult result = service.execute(decision);

            assertFalse(result.tradeExecuted());
            assertTrue(result.message().contains("Invalid action type"));
        }
    }

    // ========== BUY Execution Tests ==========

    @Nested
    @DisplayName("BUY Execution")
    class BuyExecutionTests {

        private Symbol btcSymbol;

        @BeforeEach
        void setUpBuy() {
            btcSymbol = new Symbol("BTC", InvestmentType.CRYPTO);
        }

        @Test
        @DisplayName("should fail BUY when symbol is missing")
        void shouldFailBuyWhenSymbolMissing() {
            ConsensusDecision decision = createDecision("BUY");
            // Symbol not set

            ExecutionResult result = service.execute(decision);

            assertFalse(result.tradeExecuted());
            assertTrue(result.message().contains("Symbol is missing"));
        }

        @Test
        @DisplayName("should cap BUY quantity based on available cash")
        void shouldCapBuyQuantityBasedOnCash() {
            ConsensusDecision decision = createDecisionWithSymbol("BUY", btcSymbol, new BigDecimal("10")); // Request 10 BTC

            // Available cash: $1000, BTC price: $500, so max affordable (with 95% buffer) = 1.9 BTC
            when(brokerPort.getAvailableBalance(simulationId)).thenReturn(Money.usd(new BigDecimal("1000")));

            // Price lookup
            MarketEvent priceEvent = createPriceEvent("BTC", new BigDecimal("500"), now);
            when(marketEventRepository.findByTickersAndTimeRange(any(), any(), any(), any(), eq(EventType.PRICE), eq(1)))
                .thenReturn(List.of(priceEvent));

            // Minimum trade amount check
            when(tradingLimitsConfig.crypto()).thenReturn(new BigDecimal("10")); // $10 minimum

            // Trade execution - rejected due to insufficient funds after capping
            when(brokerPort.executeTrade(any())).thenReturn(
                TradeResult.rejected(OrderId.generate(), "Insufficient funds", now)
            );

            ExecutionResult result = service.execute(decision);

            // Verify trade was attempted with capped quantity
            verify(brokerPort).executeTrade(any());
        }

        @Test
        @DisplayName("should convert BUY below minimum trade amount to HOLD")
        void shouldRejectBuyBelowMinimum() {
            ConsensusDecision decision = createDecisionWithSymbol("BUY", btcSymbol, new BigDecimal("0.001")); // Very small amount

            // Available cash
            when(brokerPort.getAvailableBalance(simulationId)).thenReturn(Money.usd(new BigDecimal("10000")));

            // Price lookup - BTC at $50,000 means 0.001 BTC = $50 (below $100 minimum)
            MarketEvent priceEvent = createPriceEvent("BTC", new BigDecimal("50000"), now);
            when(marketEventRepository.findByTickersAndTimeRange(any(), any(), any(), any(), eq(EventType.PRICE), eq(1)))
                .thenReturn(List.of(priceEvent));

            // Minimum trade amount: $100 for crypto
            when(tradingLimitsConfig.crypto()).thenReturn(new BigDecimal("100"));

            // Portfolio for snapshot (cash-only since we're converting to HOLD)
            Portfolio portfolio = createPortfolio(Money.usd(new BigDecimal("10000")), Map.of());
            when(brokerPort.getCurrentPortfolio(simulationId)).thenReturn(portfolio);
            when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ExecutionResult result = service.execute(decision);

            // Now converts to HOLD instead of rejecting — and the outcome says WHY:
            // a refused BUY is not the same thing as a deliberate HOLD.
            assertFalse(result.tradeExecuted());
            assertTrue(result.message().contains("below minimum") || result.message().contains("converted"));
            verify(consensusDecisionRepository).save(decisionCaptor.capture());
            assertEquals(ExecutionOutcome.BELOW_MINIMUM_TRADE_VALUE, decisionCaptor.getValue().getExecutionOutcome());
        }

        @Test
        @DisplayName("should execute successful BUY trade")
        void shouldExecuteSuccessfulBuy() {
            ConsensusDecision decision = createDecisionWithSymbol("BUY", btcSymbol, new BigDecimal("0.5"));

            // Available cash: plenty
            when(brokerPort.getAvailableBalance(simulationId)).thenReturn(Money.usd(new BigDecimal("100000")));

            // Price lookup
            MarketEvent priceEvent = createPriceEvent("BTC", new BigDecimal("50000"), now);
            when(marketEventRepository.findByTickersAndTimeRange(any(), any(), any(), any(), eq(EventType.PRICE), anyInt()))
                .thenReturn(List.of(priceEvent));

            // Minimum trade amount check passes ($25,000 > $100)
            when(tradingLimitsConfig.crypto()).thenReturn(new BigDecimal("100"));

            // Successful trade execution
            TradeResult tradeResult = TradeResult.executed(
                OrderId.generate(),
                Money.usd(new BigDecimal("50000")),
                new BigDecimal("0.5"),
                now
            );
            when(brokerPort.executeTrade(any())).thenReturn(tradeResult);

            // Portfolio update after trade - use cash-only portfolio (no positions)
            // This triggers the cash-only snapshot path in updatePortfolioWithMarketValue
            Portfolio portfolio = createPortfolio(Money.usd(new BigDecimal("75000")), Map.of());
            when(brokerPort.getCurrentPortfolio(simulationId)).thenReturn(portfolio);
            // Note: getPortfolioValue is NOT called for cash-only portfolios (empty positions)

            // Save snapshot
            when(portfolioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            ExecutionResult result = service.execute(decision);

            assertTrue(result.tradeExecuted());
            assertEquals("BUY", result.action());
            assertTrue(result.message().contains("executed"));

            verify(consensusDecisionRepository).save(decisionCaptor.capture());
            assertEquals(ExecutionOutcome.EXECUTED, decisionCaptor.getValue().getExecutionOutcome());
        }
    }

    // ========== SELL Execution Tests ==========

    @Nested
    @DisplayName("SELL Execution")
    class SellExecutionTests {

        private Symbol btcSymbol;

        @BeforeEach
        void setUpSell() {
            btcSymbol = new Symbol("BTC", InvestmentType.CRYPTO);
        }

        @Test
        @DisplayName("should cap SELL quantity based on holdings")
        void shouldCapSellQuantityBasedOnHoldings() {
            ConsensusDecision decision = createDecisionWithSymbol("SELL", btcSymbol, new BigDecimal("10")); // Request to sell 10 BTC

            // But only own 2 BTC
            Position position = new Position(btcSymbol, new BigDecimal("2"), Money.usd(new BigDecimal("40000")));
            Portfolio portfolio = createPortfolio(Money.usd(new BigDecimal("5000")), Map.of(btcSymbol, position));
            when(brokerPort.getCurrentPortfolio(simulationId)).thenReturn(portfolio);

            // Price lookup
            MarketEvent priceEvent = createPriceEvent("BTC", new BigDecimal("50000"), now);
            when(marketEventRepository.findByTickersAndTimeRange(any(), any(), any(), any(), eq(EventType.PRICE), anyInt()))
                .thenReturn(List.of(priceEvent));

            // Minimum trade amount (2 BTC * $50,000 = $100,000 > $100)
            when(tradingLimitsConfig.crypto()).thenReturn(new BigDecimal("100"));

            // Trade will be executed with capped quantity
            TradeResult tradeResult = TradeResult.executed(
                OrderId.generate(),
                Money.usd(new BigDecimal("50000")),
                new BigDecimal("2"), // Capped to holdings
                now
            );
            when(brokerPort.executeTrade(any())).thenReturn(tradeResult);

            // Portfolio after sell
            when(brokerPort.getPortfolioValue(eq(simulationId), any())).thenReturn(Money.usd(new BigDecimal("105000")));
            when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ExecutionResult result = service.execute(decision);

            // Should execute with capped quantity
            verify(brokerPort).executeTrade(any());
        }

        @Test
        @DisplayName("should convert SELL to HOLD when no position exists")
        void shouldSetQuantityToZeroWhenNoPosition() {
            ConsensusDecision decision = createDecisionWithSymbol("SELL", btcSymbol, new BigDecimal("1"));

            // No BTC position
            Portfolio portfolio = createPortfolio(Money.usd(new BigDecimal("10000")), Map.of());
            when(brokerPort.getCurrentPortfolio(simulationId)).thenReturn(portfolio);
            when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ExecutionResult result = service.execute(decision);

            // Should be converted to HOLD (not rejected)
            assertFalse(result.tradeExecuted());
            assertTrue(result.message().contains("no holdings") || result.message().contains("converted"));
            verify(consensusDecisionRepository).save(decisionCaptor.capture());
            assertEquals(ExecutionOutcome.NO_TRADE_REQUIRED, decisionCaptor.getValue().getExecutionOutcome());
        }
    }

    // ========== Trade Rejection Tests ==========

    @Nested
    @DisplayName("Trade Rejection")
    class TradeRejectionTests {

        private Symbol btcSymbol;

        @BeforeEach
        void setUpRejection() {
            btcSymbol = new Symbol("BTC", InvestmentType.CRYPTO);
        }

        @Test
        @DisplayName("should handle broker trade rejection")
        void shouldHandleBrokerRejection() {
            ConsensusDecision decision = createDecisionWithSymbol("BUY", btcSymbol, new BigDecimal("1"));

            // Setup mocks
            when(brokerPort.getAvailableBalance(simulationId)).thenReturn(Money.usd(new BigDecimal("100000")));
            MarketEvent priceEvent = createPriceEvent("BTC", new BigDecimal("50000"), now);
            when(marketEventRepository.findByTickersAndTimeRange(any(), any(), any(), any(), eq(EventType.PRICE), anyInt()))
                .thenReturn(List.of(priceEvent));
            when(tradingLimitsConfig.crypto()).thenReturn(new BigDecimal("100"));

            // Broker rejects trade
            TradeResult tradeResult = TradeResult.rejected(
                OrderId.generate(),
                "Market closed",
                now
            );
            when(brokerPort.executeTrade(any())).thenReturn(tradeResult);

            ExecutionResult result = service.execute(decision);

            assertFalse(result.tradeExecuted());
            assertTrue(result.message().contains("Market closed"));

            verify(consensusDecisionRepository).save(decisionCaptor.capture());
            assertEquals(ExecutionOutcome.REJECTED, decisionCaptor.getValue().getExecutionOutcome());
        }
    }

    // ========== JSON Action Parsing Tests ==========

    @Nested
    @DisplayName("JSON Action Parsing")
    class JsonParsingTests {

        @Test
        @DisplayName("should extract BUY action from JSON")
        void shouldExtractBuyFromJson() {
            ConsensusDecision decision = createDecision("{\"action\":\"BUY\",\"symbol\":\"BTC\",\"quantity\":0.5}");
            decision.setSymbol(new Symbol("BTC", InvestmentType.CRYPTO));
            decision.setQuantity(new BigDecimal("0.5"));

            // Setup minimal mocks for BUY path
            when(brokerPort.getAvailableBalance(simulationId)).thenReturn(Money.usd(new BigDecimal("100000")));
            MarketEvent priceEvent = createPriceEvent("BTC", new BigDecimal("50000"), now);
            when(marketEventRepository.findByTickersAndTimeRange(any(), any(), any(), any(), eq(EventType.PRICE), anyInt()))
                .thenReturn(List.of(priceEvent));
            when(tradingLimitsConfig.crypto()).thenReturn(new BigDecimal("100"));

            // Trade rejected for simplicity
            when(brokerPort.executeTrade(any())).thenReturn(
                TradeResult.rejected(OrderId.generate(), "Test rejection", now)
            );

            ExecutionResult result = service.execute(decision);

            // Action was parsed as BUY (trade attempted even if rejected)
            verify(brokerPort).executeTrade(any());
        }

        @Test
        @DisplayName("should extract SELL action from JSON")
        void shouldExtractSellFromJson() {
            Symbol btcSymbol = new Symbol("BTC", InvestmentType.CRYPTO);
            ConsensusDecision decision = createDecision("{\"action\":\"SELL\",\"symbol\":\"BTC\",\"quantity\":0.1}");
            decision.setSymbol(btcSymbol);
            decision.setQuantity(new BigDecimal("0.1"));

            // Setup mocks
            Position position = new Position(btcSymbol, new BigDecimal("1"), Money.usd(new BigDecimal("40000")));
            Portfolio portfolio = createPortfolio(Money.usd(new BigDecimal("5000")), Map.of(btcSymbol, position));
            when(brokerPort.getCurrentPortfolio(simulationId)).thenReturn(portfolio);

            MarketEvent priceEvent = createPriceEvent("BTC", new BigDecimal("50000"), now);
            when(marketEventRepository.findByTickersAndTimeRange(any(), any(), any(), any(), eq(EventType.PRICE), anyInt()))
                .thenReturn(List.of(priceEvent));
            when(tradingLimitsConfig.crypto()).thenReturn(new BigDecimal("100"));

            // Trade rejected for simplicity
            when(brokerPort.executeTrade(any())).thenReturn(
                TradeResult.rejected(OrderId.generate(), "Test rejection", now)
            );

            ExecutionResult result = service.execute(decision);

            // Action was parsed as SELL (trade attempted)
            verify(brokerPort).executeTrade(any());
        }
    }

    // ========== Stock Trading Tests ==========

    @Nested
    @DisplayName("Stock Trading")
    class StockTradingTests {

        @Test
        @DisplayName("should convert stock BUY below minimum to HOLD using stock minimum")
        void shouldUseStockMinimumForStocks() {
            Symbol nvdaSymbol = new Symbol("NVDA", InvestmentType.STOCK);
            ConsensusDecision decision = createDecisionWithSymbol("BUY", nvdaSymbol, new BigDecimal("0.1")); // 0.1 shares

            // Available cash
            when(brokerPort.getAvailableBalance(simulationId)).thenReturn(Money.usd(new BigDecimal("100000")));

            // NVDA at $800, so 0.1 shares = $80 (below $100 minimum for stocks)
            MarketEvent priceEvent = createPriceEvent("NVDA", new BigDecimal("800"), now);
            when(marketEventRepository.findByTickersAndTimeRange(any(), any(), any(), any(), eq(EventType.PRICE), anyInt()))
                .thenReturn(List.of(priceEvent));

            // Different minimum for stocks
            when(tradingLimitsConfig.stock()).thenReturn(new BigDecimal("100"));

            // Portfolio for snapshot
            Portfolio portfolio = createPortfolio(Money.usd(new BigDecimal("100000")), Map.of());
            when(brokerPort.getCurrentPortfolio(simulationId)).thenReturn(portfolio);
            when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ExecutionResult result = service.execute(decision);

            // Converts to HOLD instead of rejecting
            assertFalse(result.tradeExecuted());
            assertTrue(result.message().contains("below minimum") || result.message().contains("converted"));

            // Verify stock config was used
            verify(tradingLimitsConfig).stock();
            verify(tradingLimitsConfig, never()).crypto();
        }
    }

    // ========== End Date Safeguard Tests ==========

    @Nested
    @DisplayName("End Date Safeguard")
    class EndDateSafeguardTests {

        @Test
        @DisplayName("should reject portfolio snapshot when timestamp exceeds simulation end date")
        void shouldRejectSnapshotAfterEndDate() {
            // Simulation with end date in the past
            Instant endDate = Instant.parse("2025-01-31T23:59:00Z");
            com.quorumtrading.model.entity.Simulation simulation = new com.quorumtrading.model.entity.Simulation(
                simulationId,
                "Test Sim",
                new com.quorumtrading.model.valueobject.StrategyId(UUID.randomUUID()),
                Instant.parse("2020-01-01T00:00:00Z"),
                endDate,
                Money.usd(new BigDecimal("100000"))
            );
            when(simulationRepository.findById(simulationId)).thenReturn(java.util.Optional.of(simulation));

            // Attempt to create snapshot after end date
            Instant afterEndDate = Instant.parse("2026-02-22T15:12:00Z");
            PortfolioSnapshot result = service.updatePortfolioWithMarketValue(simulationId, afterEndDate);

            assertNull(result);
            verify(brokerPort, never()).getCurrentPortfolio(any());
            verify(portfolioRepository, never()).save(any());
        }

        @Test
        @DisplayName("should allow portfolio snapshot when timestamp is before simulation end date")
        void shouldAllowSnapshotBeforeEndDate() {
            Instant endDate = Instant.parse("2025-01-31T23:59:00Z");
            com.quorumtrading.model.entity.Simulation simulation = new com.quorumtrading.model.entity.Simulation(
                simulationId,
                "Test Sim",
                new com.quorumtrading.model.valueobject.StrategyId(UUID.randomUUID()),
                Instant.parse("2020-01-01T00:00:00Z"),
                endDate,
                Money.usd(new BigDecimal("100000"))
            );
            when(simulationRepository.findById(simulationId)).thenReturn(java.util.Optional.of(simulation));

            Instant beforeEndDate = Instant.parse("2024-06-15T10:00:00Z");

            // Cash-only portfolio
            Portfolio portfolio = createPortfolio(Money.usd(new BigDecimal("10000")), Map.of());
            when(brokerPort.getCurrentPortfolio(simulationId)).thenReturn(portfolio);
            when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PortfolioSnapshot result = service.updatePortfolioWithMarketValue(simulationId, beforeEndDate);

            assertNotNull(result);
            verify(portfolioRepository).save(any());
        }

        @Test
        @DisplayName("should allow portfolio snapshot when simulation has no end date")
        void shouldAllowSnapshotWhenNoEndDate() {
            com.quorumtrading.model.entity.Simulation simulation = new com.quorumtrading.model.entity.Simulation(
                simulationId,
                "Test Sim",
                new com.quorumtrading.model.valueobject.StrategyId(UUID.randomUUID()),
                Instant.parse("2020-01-01T00:00:00Z"),
                null, // No end date
                Money.usd(new BigDecimal("100000"))
            );
            when(simulationRepository.findById(simulationId)).thenReturn(java.util.Optional.of(simulation));

            // Cash-only portfolio
            Portfolio portfolio = createPortfolio(Money.usd(new BigDecimal("10000")), Map.of());
            when(brokerPort.getCurrentPortfolio(simulationId)).thenReturn(portfolio);
            when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PortfolioSnapshot result = service.updatePortfolioWithMarketValue(simulationId, Instant.now());

            assertNotNull(result);
            verify(portfolioRepository).save(any());
        }
    }

    // ========== Position Limit Tests ==========

    @Nested
    @DisplayName("Position Limits")
    class PositionLimitTests {

        /**
         * Position limits size the order at the unslipped market price, but the fill lands at
         * market + slippage, so a cap-hugging BUY settles just above the cap. Sizing against a
         * worst-case execution price keeps the realised position inside the limit.
         */
        @Test
        @DisplayName("should size a capped BUY against the execution price, not the market price")
        void shouldSizePositionLimitAgainstExecutionPrice() {
            Symbol acme = new Symbol("ACME", InvestmentType.STOCK);
            Money marketPrice = Money.usd(new BigDecimal("100"));
            Money portfolioValue = Money.usd(new BigDecimal("100000"));

            com.quorumtrading.model.orchestration.StrategyConfig config =
                mock(com.quorumtrading.model.orchestration.StrategyConfig.class);
            when(config.getPositionLimits()).thenReturn(Map.of("ACME", 0.5));

            when(brokerPort.getPortfolioValue(eq(simulationId), any())).thenReturn(portfolioValue);
            when(brokerPort.getCurrentPortfolio(simulationId))
                .thenReturn(createPortfolio(Money.usd(new BigDecimal("100000")), Map.of()));
            when(marketEventRepository.findByTickersAndTimeRange(
                    any(), any(), any(), any(), eq(EventType.PRICE), any()))
                .thenReturn(List.of(createPriceEvent("ACME", marketPrice.amount(), now)));

            // Ask for far more than the cap allows so the cap is what decides the quantity.
            BigDecimal cappedQuantity = service.enforcePositionLimit(
                simulationId, acme, new BigDecimal("100000"), config, now).quantity();

            // Unbuffered sizing gives 50000/100 = 500 shares, which fills above the 50% cap.
            assertTrue(cappedQuantity.compareTo(new BigDecimal("500")) < 0,
                "Capped quantity " + cappedQuantity + " must be below the unslipped 500 shares");

            // Worst-case fill must still sit inside the cap.
            BigDecimal worstCaseFillValue = cappedQuantity.multiply(new BigDecimal("100.5"));
            BigDecimal cap = portfolioValue.amount().multiply(new BigDecimal("0.5"));
            assertTrue(worstCaseFillValue.compareTo(cap) <= 0,
                "Worst-case fill " + worstCaseFillValue + " exceeds the cap " + cap);
        }
    }

    // ========== Order Size Policy & Risk Block Reasons ==========

    @Nested
    @DisplayName("Order Size Policy")
    class OrderSizePolicyTests {

        private Symbol btcSymbol;

        @BeforeEach
        void setUpSymbol() {
            btcSymbol = new Symbol("BTC", InvestmentType.CRYPTO);
        }

        private void stubBtcAt50k() {
            MarketEvent priceEvent = createPriceEvent("BTC", new BigDecimal("50000"), now);
            when(marketEventRepository.findByTickersAndTimeRange(
                    any(), any(), any(), any(), eq(EventType.PRICE), anyInt()))
                .thenReturn(List.of(priceEvent));
        }

        private void stubStrategyConfig(com.quorumtrading.model.orchestration.StrategyConfig config) {
            com.quorumtrading.model.entity.Simulation simulation = new com.quorumtrading.model.entity.Simulation(
                simulationId,
                "Test Sim",
                new com.quorumtrading.model.valueobject.StrategyId(UUID.randomUUID()),
                Instant.parse("2020-01-01T00:00:00Z"),
                null,
                Money.usd(new BigDecimal("100000"))
            );
            when(simulationRepository.findById(simulationId)).thenReturn(java.util.Optional.of(simulation));
            com.quorumtrading.model.entity.Strategy strategy = mock(com.quorumtrading.model.entity.Strategy.class);
            when(strategy.getContent()).thenReturn("---\n---\nbody");
            when(strategyRepository.findById(any())).thenReturn(java.util.Optional.of(strategy));
            when(strategyConfigParser.parse(anyString())).thenReturn(config);
        }

        @Test
        @DisplayName("quantity-less BUY is platform-sized to 25% of portfolio value, not the whole allowance")
        void shouldSizeQuantityLessBuyToDefaultOrderFraction() {
            ConsensusDecision decision = createDecisionWithSymbol("BUY", btcSymbol, null);

            when(brokerPort.getAvailableBalance(simulationId)).thenReturn(Money.usd(new BigDecimal("100000")));
            when(brokerPort.getPortfolioValue(eq(simulationId), any())).thenReturn(Money.usd(new BigDecimal("100000")));
            stubBtcAt50k();
            when(tradingLimitsConfig.crypto()).thenReturn(new BigDecimal("100"));
            when(brokerPort.executeTrade(any())).thenAnswer(inv -> {
                com.quorumtrading.model.broker.ExecuteTradeCommand cmd = inv.getArgument(0);
                return TradeResult.executed(OrderId.generate(), Money.usd(new BigDecimal("50000")), cmd.quantity(), now);
            });
            when(brokerPort.getCurrentPortfolio(simulationId))
                .thenReturn(createPortfolio(Money.usd(new BigDecimal("75000")), Map.of()));
            when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ExecutionResult result = service.execute(decision);

            assertTrue(result.tradeExecuted());
            ArgumentCaptor<com.quorumtrading.model.broker.ExecuteTradeCommand> commandCaptor =
                ArgumentCaptor.forClass(com.quorumtrading.model.broker.ExecuteTradeCommand.class);
            verify(brokerPort).executeTrade(commandCaptor.capture());
            // 25% of the $100,000 portfolio = $25,000, at execution price $50,250 -> 0.49751243 BTC.
            // The old default would have bought 1.99 BTC (the entire affordable cash pile).
            assertEquals(0, commandCaptor.getValue().quantity().compareTo(new BigDecimal("0.49751243")),
                "expected 0.49751243 but was " + commandCaptor.getValue().quantity());
            // The decision itself must say the platform chose the size, and how to control it.
            assertNotNull(decision.getNotes());
            assertTrue(decision.getNotes().contains("platform-sized"));
            assertTrue(decision.getNotes().contains("max_order_fraction"));
        }

        @Test
        @DisplayName("max_order_fraction caps even an explicit agent quantity")
        void shouldCapExplicitQuantityWithConfiguredMaxOrderFraction() {
            com.quorumtrading.model.orchestration.StrategyConfig config =
                new com.quorumtrading.model.orchestration.StrategyConfig();
            config.setMaxOrderFraction(0.1);
            config.setReserveCashPercentage(0.0);
            stubStrategyConfig(config);

            ConsensusDecision decision = createDecisionWithSymbol("BUY", btcSymbol, new BigDecimal("1.0"));

            when(brokerPort.getAvailableBalance(simulationId)).thenReturn(Money.usd(new BigDecimal("100000")));
            when(brokerPort.getPortfolioValue(eq(simulationId), any())).thenReturn(Money.usd(new BigDecimal("100000")));
            stubBtcAt50k();
            when(tradingLimitsConfig.crypto()).thenReturn(new BigDecimal("100"));
            when(brokerPort.executeTrade(any())).thenAnswer(inv -> {
                com.quorumtrading.model.broker.ExecuteTradeCommand cmd = inv.getArgument(0);
                return TradeResult.executed(OrderId.generate(), Money.usd(new BigDecimal("50000")), cmd.quantity(), now);
            });
            when(brokerPort.getCurrentPortfolio(simulationId))
                .thenReturn(createPortfolio(Money.usd(new BigDecimal("90000")), Map.of()));
            when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ExecutionResult result = service.execute(decision);

            assertTrue(result.tradeExecuted());
            ArgumentCaptor<com.quorumtrading.model.broker.ExecuteTradeCommand> commandCaptor =
                ArgumentCaptor.forClass(com.quorumtrading.model.broker.ExecuteTradeCommand.class);
            verify(brokerPort).executeTrade(commandCaptor.capture());
            // 10% of $100,000 = $10,000 at execution price $50,250 -> 0.19900497 BTC, not the 1.0 asked.
            assertEquals(0, commandCaptor.getValue().quantity().compareTo(new BigDecimal("0.19900497")),
                "expected 0.19900497 but was " + commandCaptor.getValue().quantity());
            assertNotNull(decision.getNotes());
            assertTrue(decision.getNotes().contains("max_order_fraction (0.1) caps this BUY"));
        }

        @Test
        @DisplayName("exhausted position limit blocks BUY with BLOCKED_BY_RISK_LIMITS and names both numbers")
        void shouldBlockWithNamedPositionLimit() {
            com.quorumtrading.model.orchestration.StrategyConfig config =
                new com.quorumtrading.model.orchestration.StrategyConfig();
            config.setPositionLimits(Map.of("BTC", 0.02));
            config.setReserveCashPercentage(0.0);
            stubStrategyConfig(config);

            ConsensusDecision decision = createDecisionWithSymbol("BUY", btcSymbol, new BigDecimal("0.5"));

            when(brokerPort.getAvailableBalance(simulationId)).thenReturn(Money.usd(new BigDecimal("3000")));
            when(brokerPort.getPortfolioValue(eq(simulationId), any())).thenReturn(Money.usd(new BigDecimal("100000")));
            stubBtcAt50k();
            // Holding 0.04 BTC at $50,000 = $2,000 = exactly the 2% allowance: no room left.
            Position held = new Position(btcSymbol, new BigDecimal("0.04"), Money.usd(new BigDecimal("45000")));
            when(brokerPort.getCurrentPortfolio(simulationId))
                .thenReturn(createPortfolio(Money.usd(new BigDecimal("3000")), Map.of(btcSymbol, held)));
            when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ExecutionResult result = service.execute(decision);

            assertFalse(result.tradeExecuted());
            verify(brokerPort, never()).executeTrade(any());
            verify(consensusDecisionRepository).save(decisionCaptor.capture());
            ConsensusDecision saved = decisionCaptor.getValue();
            assertEquals(ExecutionOutcome.BLOCKED_BY_RISK_LIMITS, saved.getExecutionOutcome());
            // The note names the control, its configured value, and the measured holding.
            assertNotNull(saved.getNotes());
            assertTrue(saved.getNotes().contains("position_limits.BTC (0.02) reached"),
                "note was: " + saved.getNotes());
            assertTrue(saved.getNotes().contains("2.0% of portfolio"),
                "note was: " + saved.getNotes());
        }

        @Test
        @DisplayName("reserve-cash block names the reserve and both cash numbers")
        void reserveCashBlockNamesBothNumbers() {
            com.quorumtrading.model.orchestration.StrategyConfig config =
                new com.quorumtrading.model.orchestration.StrategyConfig();
            config.setReserveCashPercentage(0.95);

            when(brokerPort.getPortfolioValue(eq(simulationId), any())).thenReturn(Money.usd(new BigDecimal("10000")));
            when(brokerPort.getAvailableBalance(simulationId)).thenReturn(Money.usd(new BigDecimal("500")));
            stubBtcAt50k();

            ExecuteConsensusDecisionService.RiskCap cap = service.enforceReserveCash(
                simulationId, new BigDecimal("0.01"), config, btcSymbol, now);

            assertEquals(0, BigDecimal.ZERO.compareTo(cap.quantity()));
            assertNotNull(cap.blockReason());
            assertTrue(cap.blockReason().contains("reserve_cash_percentage (0.95)"),
                "reason was: " + cap.blockReason());
            assertTrue(cap.blockReason().contains("$500.00"), "reason was: " + cap.blockReason());
            assertTrue(cap.blockReason().contains("$9500.00"), "reason was: " + cap.blockReason());
        }
    }

    // ========== Snapshot Price Freshness Tests ==========

    @Nested
    @DisplayName("Snapshot Price Freshness")
    class SnapshotPriceFreshnessTests {

        /**
         * The market-event repository returns PRICE rows ORDER BY timestamp DESC, so the
         * snapshot must take the FIRST event per symbol, not the last one it happens to see.
         * Storing the oldest price in the 24h look-back window leaves the persisted position
         * priced hours behind the broker-reported totalValue, and the row no longer reconciles
         * as cash + sum(qty x currentPrice).
         */
        @Test
        @DisplayName("should store the latest price per symbol, not the oldest in the look-back window")
        void shouldStoreLatestPriceNotOldest() {
            Instant snapshotAt = Instant.parse("2026-06-23T16:00:00Z");
            Symbol btc = new Symbol("BTCUSD", InvestmentType.CRYPTO);
            BigDecimal quantity = new BigDecimal("0.5");

            Position position = new Position(btc, quantity, Money.usd(new BigDecimal("60000")));
            Portfolio portfolio = createPortfolio(
                Money.usd(new BigDecimal("54769.43")), Map.of(btc, position));
            when(brokerPort.getCurrentPortfolio(simulationId)).thenReturn(portfolio);

            // Broker prices the position at the LATEST price: 0.5 x 70000 = 35000
            Money latestPrice = Money.usd(new BigDecimal("70000"));
            Money brokerValue = Money.usd(new BigDecimal("89769.43"));
            when(brokerPort.getPortfolioValue(eq(simulationId), any())).thenReturn(brokerValue);

            // Repository order: newest first (ORDER BY timestamp DESC)
            when(marketEventRepository.findByTickersAndTimeRange(
                    any(), any(), any(), any(), eq(EventType.PRICE), any()))
                .thenReturn(List.of(
                    createPriceEvent("BTCUSD", latestPrice.amount(), snapshotAt.minusSeconds(60)),
                    createPriceEvent("BTCUSD", new BigDecimal("68200"), snapshotAt.minusSeconds(3600)),
                    createPriceEvent("BTCUSD", new BigDecimal("68176.14"), snapshotAt.minusSeconds(82800))
                ));
            when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PortfolioSnapshot saved = service.updatePortfolioWithMarketValue(simulationId, snapshotAt);

            assertNotNull(saved);
            Position storedPosition = saved.getPositions().get(btc);
            assertNotNull(storedPosition);
            assertEquals(0, latestPrice.amount().compareTo(storedPosition.currentPrice().amount()),
                "Snapshot must store the most recent price, got " + storedPosition.currentPrice());
        }

        @Test
        @DisplayName("should reconcile totalValue as cash + position market value")
        void shouldReconcileTotalValueWithPositions() {
            Instant snapshotAt = Instant.parse("2026-06-23T16:00:00Z");
            Symbol btc = new Symbol("BTCUSD", InvestmentType.CRYPTO);
            BigDecimal quantity = new BigDecimal("0.5");
            Money cash = Money.usd(new BigDecimal("54769.43"));

            Position position = new Position(btc, quantity, Money.usd(new BigDecimal("60000")));
            when(brokerPort.getCurrentPortfolio(simulationId))
                .thenReturn(createPortfolio(cash, Map.of(btc, position)));
            when(brokerPort.getPortfolioValue(eq(simulationId), any()))
                .thenReturn(Money.usd(new BigDecimal("89769.43")));
            when(marketEventRepository.findByTickersAndTimeRange(
                    any(), any(), any(), any(), eq(EventType.PRICE), any()))
                .thenReturn(List.of(
                    createPriceEvent("BTCUSD", new BigDecimal("70000"), snapshotAt.minusSeconds(60)),
                    createPriceEvent("BTCUSD", new BigDecimal("68176.14"), snapshotAt.minusSeconds(82800))
                ));
            when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PortfolioSnapshot saved = service.updatePortfolioWithMarketValue(simulationId, snapshotAt);

            assertNotNull(saved);
            Money positionsValue = Money.usd(BigDecimal.ZERO);
            for (Position p : saved.getPositions().values()) {
                assertNotNull(p.currentPrice(), "position " + p.symbol().ticker() + " has no current price");
                positionsValue = positionsValue.add(p.currentPrice().multiply(p.quantity()));
            }
            Money reconciled = saved.getCashAvailable().add(positionsValue);

            assertEquals(0, reconciled.amount().compareTo(saved.getTotalValue().amount()),
                "totalValue " + saved.getTotalValue() + " must equal cash + positions " + reconciled);
        }
    }

    // ========== Event Publishing Tests ==========

    @Nested
    @DisplayName("Event Publishing")
    class EventPublishingTests {

        @Test
        @DisplayName("should publish TradeExecutionEvent on successful trade")
        void shouldPublishEventOnSuccessfulTrade() {
            Symbol btcSymbol = new Symbol("BTC", InvestmentType.CRYPTO);
            ConsensusDecision decision = createDecisionWithSymbol("BUY", btcSymbol, new BigDecimal("0.5"));

            // Setup successful trade
            when(brokerPort.getAvailableBalance(simulationId)).thenReturn(Money.usd(new BigDecimal("100000")));
            MarketEvent priceEvent = createPriceEvent("BTC", new BigDecimal("50000"), now);
            when(marketEventRepository.findByTickersAndTimeRange(any(), any(), any(), any(), eq(EventType.PRICE), anyInt()))
                .thenReturn(List.of(priceEvent));
            when(tradingLimitsConfig.crypto()).thenReturn(new BigDecimal("100"));

            TradeResult tradeResult = TradeResult.executed(
                OrderId.generate(),
                Money.usd(new BigDecimal("50000")),
                new BigDecimal("0.5"),
                now
            );
            when(brokerPort.executeTrade(any())).thenReturn(tradeResult);

            // Cash-only portfolio - getPortfolioValue not called for empty positions
            Portfolio portfolio = createPortfolio(Money.usd(new BigDecimal("75000")), Map.of());
            when(brokerPort.getCurrentPortfolio(simulationId)).thenReturn(portfolio);
            when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.execute(decision);

            verify(tradeExecutionEventPublisher).publish(tradeEventCaptor.capture());
            TradeExecutionEvent event = tradeEventCaptor.getValue();
            assertEquals("BUY", event.getAction());
            assertTrue(event.isSuccess());
            assertEquals("BTC", event.getSymbol());
            assertEquals(new BigDecimal("0.5"), event.getQuantity());
        }

        @Test
        @DisplayName("should publish PortfolioChangeEvent after successful trade")
        void shouldPublishPortfolioChangeEventAfterTrade() {
            Symbol btcSymbol = new Symbol("BTC", InvestmentType.CRYPTO);
            ConsensusDecision decision = createDecisionWithSymbol("BUY", btcSymbol, new BigDecimal("0.5"));

            // Setup successful trade
            when(brokerPort.getAvailableBalance(simulationId)).thenReturn(Money.usd(new BigDecimal("100000")));
            MarketEvent priceEvent = createPriceEvent("BTC", new BigDecimal("50000"), now);
            when(marketEventRepository.findByTickersAndTimeRange(any(), any(), any(), any(), eq(EventType.PRICE), anyInt()))
                .thenReturn(List.of(priceEvent));
            when(tradingLimitsConfig.crypto()).thenReturn(new BigDecimal("100"));

            TradeResult tradeResult = TradeResult.executed(
                OrderId.generate(),
                Money.usd(new BigDecimal("50000")),
                new BigDecimal("0.5"),
                now
            );
            when(brokerPort.executeTrade(any())).thenReturn(tradeResult);

            // Cash-only portfolio - getPortfolioValue not called for empty positions
            Portfolio portfolio = createPortfolio(Money.usd(new BigDecimal("75000")), Map.of());
            when(brokerPort.getCurrentPortfolio(simulationId)).thenReturn(portfolio);
            when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.execute(decision);

            verify(portfolioChangeEventPublisher).publish(any(PortfolioChangeEvent.class));
        }
    }
}
