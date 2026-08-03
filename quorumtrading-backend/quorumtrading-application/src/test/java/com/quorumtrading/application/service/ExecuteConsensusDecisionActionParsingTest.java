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
import com.quorumtrading.application.usecase.ExecuteConsensusDecisionUseCase.ExecutionResult;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.enums.ConsensusMethod;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.ExecutionOutcome;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CHARACTERIZATION TESTS for how {@link ExecuteConsensusDecisionService} extracts the
 * action out of {@code ConsensusDecision.finalAction} (the "P1" parser on the
 * real-money trade-execution path).
 *
 * <p>These tests pin the CURRENT behavior — including its deliberate leniency
 * (salvaging malformed JSON and still trading) and its exact failure strings — so the
 * typed {@code ConsensusAction} refactor can prove behavior preservation.</p>
 *
 * <p>Behavior pinned here (P1 semantics on main):</p>
 * <ul>
 *   <li>null / blank finalAction → {@code tradeFailed("UNKNOWN", "Final action is null or empty")}</li>
 *   <li>plain token, case-insensitive, whitespace-tolerant → resolved action</li>
 *   <li>valid JSON with action key → resolved action (case-insensitive)</li>
 *   <li><b>malformed/unterminated JSON with a recognizable action → STILL TRADES</b></li>
 *   <li>JSON without an action key → tradeFailed carrying the WHOLE JSON string</li>
 *   <li>unresolvable action token → tradeFailed carrying the extracted token</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecuteConsensusDecisionActionParsingTest {

    @Mock private BrokerPort brokerPort;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private ConsensusDecisionRepository consensusDecisionRepository;
    @Mock private MarketEventRepository marketEventRepository;
    @Mock private EventPublisher<TradeExecutionEvent> tradeExecutionEventPublisher;
    @Mock private EventPublisher<PortfolioChangeEvent> portfolioChangeEventPublisher;
    @Mock private TradingLimitsConfig tradingLimitsConfig;
    @Mock private SimulationRepository simulationRepository;
    @Mock private StrategyRepository strategyRepository;
    @Mock private StrategyConfigParser strategyConfigParser;

    @Captor private ArgumentCaptor<ConsensusDecision> decisionCaptor;

    private ExecuteConsensusDecisionService service;
    private SimulationId simulationId;
    private Instant now;
    private Symbol btcSymbol;

    @BeforeEach
    void setUp() {
        service = new ExecuteConsensusDecisionService(
            brokerPort, portfolioRepository, consensusDecisionRepository, marketEventRepository,
            tradeExecutionEventPublisher, portfolioChangeEventPublisher, tradingLimitsConfig,
            simulationRepository, strategyRepository, strategyConfigParser);
        simulationId = new SimulationId(UUID.randomUUID());
        now = Instant.now();
        btcSymbol = new Symbol("BTC", InvestmentType.CRYPTO);
    }

    private ConsensusDecision decisionOf(String finalAction) {
        ConsensusDecision decision = new ConsensusDecision(
            simulationId, now, ConsensusMethod.MAJORITY,
            com.quorumtrading.application.orchestration.agents.ConsensusActionParser.parse(finalAction));
        decision.setId(1L);
        return decision;
    }

    private ConsensusDecision buyDecisionOf(String finalAction) {
        ConsensusDecision decision = decisionOf(finalAction);
        decision.setSymbol(btcSymbol);
        decision.setQuantity(new BigDecimal("0.5"));
        decision.setInvestmentType(InvestmentType.CRYPTO);
        return decision;
    }

    /** Wire the full BUY happy path up to a (rejected) broker call. */
    private void mockBuyPath() {
        when(brokerPort.getAvailableBalance(simulationId)).thenReturn(Money.usd(new BigDecimal("100000")));
        MarketEvent priceEvent = new MarketEvent(now, EventType.PRICE, "test-source", "BTC",
            Map.of("price", "50000"));
        when(marketEventRepository.findByTickersAndTimeRange(any(), any(), any(), any(), eq(EventType.PRICE), anyInt()))
            .thenReturn(List.of(priceEvent));
        when(tradingLimitsConfig.crypto()).thenReturn(new BigDecimal("100"));
        when(brokerPort.executeTrade(any())).thenReturn(
            TradeResult.rejected(OrderId.generate(), "Test rejection", now));
    }

    // ---------- null / blank ----------

    @Nested
    @DisplayName("null / blank finalAction")
    class NullOrBlank {

        @Test
        void nullFinalActionFailsWithUnknownAction() {
            ExecutionResult result = service.execute(decisionOf(null));

            assertThat(result.tradeExecuted()).isFalse();
            assertThat(result.action()).isEqualTo("UNKNOWN");
            assertThat(result.message()).isEqualTo("Trade failed: Final action is null or empty");
        }

        @Test
        void blankFinalActionFailsWithUnknownAction() {
            ExecutionResult result = service.execute(decisionOf("   "));

            assertThat(result.tradeExecuted()).isFalse();
            assertThat(result.action()).isEqualTo("UNKNOWN");
            assertThat(result.message()).isEqualTo("Trade failed: Final action is null or empty");
        }
    }

    // ---------- plain tokens ----------

    @Nested
    @DisplayName("plain (non-JSON) action tokens")
    class PlainTokens {

        @Test
        void plainLowercaseBuyWithPaddingExecutesTrade() {
            mockBuyPath();

            ExecutionResult result = service.execute(buyDecisionOf("  buy  "));

            verify(brokerPort).executeTrade(any());
            assertThat(result.action()).isEqualTo("BUY");
        }

        @Test
        void plainHoldRequiresNoTrade() {
            ExecutionResult result = service.execute(decisionOf("HOLD"));

            assertThat(result.tradeExecuted()).isFalse();
            assertThat(result.action()).isEqualTo("HOLD");
            verify(brokerPort, never()).executeTrade(any());
            verify(consensusDecisionRepository).save(decisionCaptor.capture());
            assertThat(decisionCaptor.getValue().getExecutionOutcome())
                .isEqualTo(ExecutionOutcome.NO_TRADE_REQUIRED);
        }

        @Test
        void plainSkipRequiresNoTrade() {
            ExecutionResult result = service.execute(decisionOf("SKIP"));

            assertThat(result.tradeExecuted()).isFalse();
            assertThat(result.action()).isEqualTo("SKIP");
            verify(consensusDecisionRepository).save(decisionCaptor.capture());
            assertThat(decisionCaptor.getValue().getExecutionOutcome())
                .isEqualTo(ExecutionOutcome.SKIPPED);
        }

        @Test
        void unresolvablePlainTokenFailsCarryingTheToken() {
            ExecutionResult result = service.execute(decisionOf("FOO"));

            assertThat(result.tradeExecuted()).isFalse();
            assertThat(result.action()).isEqualTo("FOO");
            assertThat(result.message()).isEqualTo("Trade failed: Invalid action type: FOO");
        }
    }

    // ---------- valid JSON ----------

    @Nested
    @DisplayName("valid JSON finalAction")
    class ValidJson {

        @Test
        void w1ShapedHoldJsonRequiresNoTrade() {
            ExecutionResult result = service.execute(decisionOf(
                "{\"action\":\"HOLD\",\"symbol\":\"BTC\",\"quantity\":0.0,\"confidence\":0.5,\"reasoning\":\"wait\"}"));

            assertThat(result.tradeExecuted()).isFalse();
            assertThat(result.action()).isEqualTo("HOLD");
        }

        @Test
        void lowercaseJsonActionResolvesCaseInsensitively() {
            ExecutionResult result = service.execute(decisionOf("{\"action\":\"skip\",\"symbol\":\"BTC\"}"));

            assertThat(result.tradeExecuted()).isFalse();
            assertThat(result.action()).isEqualTo("SKIP");
        }

        @Test
        void jsonBuyExecutesTrade() {
            mockBuyPath();

            ExecutionResult result = service.execute(buyDecisionOf(
                "{\"action\":\"BUY\",\"symbol\":\"BTC\",\"quantity\":0.5,\"confidence\":0.9,\"reasoning\":\"up\"}"));

            verify(brokerPort).executeTrade(any());
            assertThat(result.action()).isEqualTo("BUY");
        }

        @Test
        void jsonSellWithNoHoldingsConvertsToHold() {
            // Proves SELL was parsed: the no-holdings SELL→HOLD conversion path runs.
            Portfolio portfolio = new Portfolio(simulationId, Map.of(), Money.usd(new BigDecimal("10000")));
            when(brokerPort.getCurrentPortfolio(simulationId)).thenReturn(portfolio);
            when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ConsensusDecision decision = decisionOf("{\"action\":\"SELL\",\"symbol\":\"BTC\",\"quantity\":1}");
            decision.setSymbol(btcSymbol);
            decision.setQuantity(BigDecimal.ONE);

            ExecutionResult result = service.execute(decision);

            assertThat(result.tradeExecuted()).isFalse();
            assertThat(result.action()).isEqualTo("HOLD (converted)");
            verify(consensusDecisionRepository).save(decisionCaptor.capture());
            assertThat(decisionCaptor.getValue().getNotes()).contains("no holdings for BTC");
        }

        @Test
        void jsonWithoutActionKeyFailsCarryingWholeJson() {
            String json = "{\"symbol\":\"BTC\",\"quantity\":1}";

            ExecutionResult result = service.execute(decisionOf(json));

            assertThat(result.tradeExecuted()).isFalse();
            assertThat(result.action()).isEqualTo(json);
            assertThat(result.message()).isEqualTo("Trade failed: Invalid action type: " + json);
        }

        @Test
        void jsonWithUnresolvableActionFailsCarryingTheToken() {
            ExecutionResult result = service.execute(decisionOf("{\"action\":\"FOO\",\"symbol\":\"BTC\"}"));

            assertThat(result.tradeExecuted()).isFalse();
            assertThat(result.action()).isEqualTo("FOO");
            assertThat(result.message()).isEqualTo("Trade failed: Invalid action type: FOO");
        }

        @Test
        void jsonWithNumericActionFailsWithExtractedToken() {
            // UNIFICATION U6 (failure-message only): the legacy char-scan read until '"'
            // or ',' so an unquoted numeric value dragged the closing brace along ("5}").
            // The typed parser (Jackson) extracts the clean token "5". Either way the
            // trade fails — only the token in the failure message changed.
            ExecutionResult result = service.execute(decisionOf("{\"action\": 5}"));

            assertThat(result.tradeExecuted()).isFalse();
            assertThat(result.action()).isEqualTo("5");
            assertThat(result.message()).isEqualTo("Trade failed: Invalid action type: 5");
        }
    }

    // ---------- malformed JSON (the load-bearing lenient path) ----------

    @Nested
    @DisplayName("malformed JSON finalAction")
    class MalformedJson {

        @Test
        void unterminatedJsonWithRecognizableBuyStillExecutesTrade() {
            // LOAD-BEARING LENIENCY: the money path salvages malformed JSON and trades.
            mockBuyPath();

            ExecutionResult result = service.execute(buyDecisionOf("{\"action\":\"BUY\""));

            verify(brokerPort).executeTrade(any());
            assertThat(result.action()).isEqualTo("BUY");
        }

        @Test
        void unterminatedJsonWithUnresolvableActionFailsCarryingTheToken() {
            ExecutionResult result = service.execute(decisionOf("{\"action\":\"FOO\""));

            assertThat(result.tradeExecuted()).isFalse();
            assertThat(result.action()).isEqualTo("FOO");
            assertThat(result.message()).isEqualTo("Trade failed: Invalid action type: FOO");
        }

        @Test
        void malformedJsonWithoutActionKeyFailsCarryingWholeString() {
            String malformed = "{broken json";

            ExecutionResult result = service.execute(decisionOf(malformed));

            assertThat(result.tradeExecuted()).isFalse();
            assertThat(result.action()).isEqualTo(malformed);
            assertThat(result.message()).isEqualTo("Trade failed: Invalid action type: " + malformed);
        }
    }
}
