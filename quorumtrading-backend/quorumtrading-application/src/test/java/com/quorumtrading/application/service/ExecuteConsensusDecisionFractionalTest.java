package com.quorumtrading.application.service;

import com.quorumtrading.application.orchestration.agents.ConsensusActionParser;
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
import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.enums.ConsensusMethod;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.ExecutionOutcome;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import com.quorumtrading.model.valueobject.BrokerCapabilities;
import com.quorumtrading.model.valueobject.MIC;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.OrderId;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FX2 — per-simulation broker-capability wiring in the consensus BUY pipeline.
 *
 * <p>Verifies that the final order quantity is rounded to the routed broker's lot
 * granularity (resolved per-simulation via {@link BrokerRouter#capabilitiesForSimulation})
 * as the LAST sizing step:</p>
 * <ul>
 *   <li><b>Whole-share (Saxo EU) broker, EUR instrument:</b> €1000 @ €150 → 6 whole shares,
 *       with the ~€100 residue left unspent (carried to the next tick).</li>
 *   <li><b>Whole-share broker, sub-one-share budget:</b> the rounded quantity is 0, so the BUY
 *       converts to HOLD with a clear reason rather than sending a 0-share order.</li>
 *   <li><b>Fractional (Alpaca / Mock) broker:</b> the quantity keeps its fractional component —
 *       byte-identical to the historical scale-8 floor.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecuteConsensusDecisionService — FX2 per-simulation lot rounding")
class ExecuteConsensusDecisionFractionalTest {

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
    @Mock
    private BrokerRouter brokerRouter;

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
                strategyConfigParser,
                brokerRouter);
        simulationId = new SimulationId(UUID.randomUUID());
        now = Instant.now();
    }

    private ConsensusDecision decisionWithSymbol(Symbol symbol) {
        ConsensusDecision decision = new ConsensusDecision(
                simulationId, now, ConsensusMethod.MAJORITY, ConsensusActionParser.parse("BUY"));
        decision.setId(1L);
        decision.setSymbol(symbol);
        // No agent quantity → service sizes from available cash, so the rounding is what
        // determines the final order quantity (not an LLM-supplied number).
        decision.setInvestmentType(symbol.type());
        return decision;
    }

    private MarketEvent priceEvent(String ticker, BigDecimal price) {
        return new MarketEvent(now, EventType.PRICE, "test-source", ticker, Map.of("price", price.toString()));
    }

    /** EU instrument on Xetra → EUR settlement currency. */
    private Symbol euStock(String ticker) {
        return new Symbol(ticker, InvestmentType.STOCK, new MIC("XETR"), Money.Currency.EUR, null, null);
    }

    @Test
    @DisplayName("EUR whole-share BUY: €1000 @ €150 rounds to 6 shares, leaving the residue unspent")
    void wholeShareEuBuyRoundsToSixSharesWithResidue() {
        Symbol rhm = euStock("RHM");
        ConsensusDecision decision = decisionWithSymbol(rhm);

        // Routed broker for this simulation = whole-share equities (Saxo EU).
        when(brokerRouter.capabilitiesForSimulation(simulationId))
                .thenReturn(BrokerCapabilities.wholeShareEquities());

        // €1000 cash, €150/share. Unbuffered: 1000/150 = 6.666… → 6 whole shares,
        // residue = 1000 - 6*150 = €100 left unspent.
        when(brokerPort.getAvailableBalance(simulationId))
                .thenReturn(Money.of(new BigDecimal("1000"), Money.Currency.EUR));
        when(marketEventRepository.findByTickersAndTimeRange(any(), any(), any(), any(), eq(EventType.PRICE), anyInt()))
                .thenReturn(List.of(priceEvent("RHM", new BigDecimal("150"))));

        // Low min-trade floor so 6 * €150 = €900 clears it.
        when(tradingLimitsConfig.stock()).thenReturn(new BigDecimal("100"));

        // Successful fill of the 6-share order.
        when(brokerPort.executeTrade(any())).thenReturn(TradeResult.executed(
                OrderId.generate(), Money.of(new BigDecimal("150"), Money.Currency.EUR), new BigDecimal("6"), now));

        // Post-trade portfolio (cash-only snapshot path).
        Portfolio portfolio = new Portfolio(
                simulationId, Map.of(), Money.of(new BigDecimal("100"), Money.Currency.EUR));
        when(brokerPort.getCurrentPortfolio(simulationId)).thenReturn(portfolio);
        when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExecutionResult result = service.execute(decision);

        assertTrue(result.tradeExecuted(), "trade should execute");
        ArgumentCaptor<ExecuteTradeCommand> cmd = ArgumentCaptor.forClass(ExecuteTradeCommand.class);
        verify(brokerPort).executeTrade(cmd.capture());
        // FINAL order quantity is an integral 6 shares — no fractional component.
        BigDecimal finalQty = cmd.getValue().quantity();
        assertEquals(0, finalQty.compareTo(new BigDecimal("6")),
                "BUY quantity must round down to 6 whole shares, was: " + finalQty);
        assertTrue(finalQty.stripTrailingZeros().scale() <= 0,
                "rounded quantity must be integral (no fractional digits), was: " + finalQty);
    }

    @Test
    @DisplayName("Whole-share BUY rounding to 0 converts to HOLD with a clear reason")
    void wholeShareBuyRoundingToZeroConvertsToHold() {
        Symbol rhm = euStock("RHM");
        ConsensusDecision decision = decisionWithSymbol(rhm);

        when(brokerRouter.capabilitiesForSimulation(simulationId))
                .thenReturn(BrokerCapabilities.wholeShareEquities());

        // €100 cash, €150/share → 0 whole shares affordable → must HOLD.
        when(brokerPort.getAvailableBalance(simulationId))
                .thenReturn(Money.of(new BigDecimal("100"), Money.Currency.EUR));
        when(marketEventRepository.findByTickersAndTimeRange(any(), any(), any(), any(), eq(EventType.PRICE), anyInt()))
                .thenReturn(List.of(priceEvent("RHM", new BigDecimal("150"))));

        // Min-trade floor is irrelevant here (HOLD happens before the min check), but keep it
        // lenient so an unexpected execution path would still fail loudly rather than NPE.
        lenient().when(tradingLimitsConfig.stock()).thenReturn(new BigDecimal("100"));

        // Portfolio for the HOLD snapshot.
        Portfolio portfolio = new Portfolio(
                simulationId, Map.of(), Money.of(new BigDecimal("100"), Money.Currency.EUR));
        when(brokerPort.getCurrentPortfolio(simulationId)).thenReturn(portfolio);
        when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExecutionResult result = service.execute(decision);

        assertFalse(result.tradeExecuted(), "must NOT execute a 0-share order");
        verify(brokerPort, never()).executeTrade(any());

        ArgumentCaptor<ConsensusDecision> saved = ArgumentCaptor.forClass(ConsensusDecision.class);
        verify(consensusDecisionRepository).save(saved.capture());
        // A BUY the engine could not fill is not a deliberate HOLD: the outcome says the
        // order was too small to execute (budget below one whole share).
        assertEquals(ExecutionOutcome.BELOW_MINIMUM_TRADE_VALUE, saved.getValue().getExecutionOutcome());

        // Clear, whole-share reason recorded on the decision and surfaced in the event.
        ArgumentCaptor<TradeExecutionEvent> evt = ArgumentCaptor.forClass(TradeExecutionEvent.class);
        verify(tradeExecutionEventPublisher).publish(evt.capture());
        String message = evt.getValue().getMessage();
        assertTrue(message.contains("HOLD"), "reason should explain the conversion to HOLD: " + message);
        assertTrue(message.toLowerCase().contains("whole share")
                        || message.toLowerCase().contains("0 whole shares"),
                "reason should explain the whole-share rounding: " + message);
    }

    @Test
    @DisplayName("Fractional broker keeps the fractional quantity (byte-identical to legacy path)")
    void fractionalBrokerKeepsFractionalQuantity() {
        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        ConsensusDecision decision = new ConsensusDecision(
                simulationId, now, ConsensusMethod.MAJORITY, ConsensusActionParser.parse("BUY"));
        decision.setId(2L);
        decision.setSymbol(btc);
        decision.setInvestmentType(btc.type());

        // Fractional broker for this simulation (Alpaca / Mock semantics).
        when(brokerRouter.capabilitiesForSimulation(simulationId))
                .thenReturn(BrokerCapabilities.fractional());

        // $1000 cash, $150/coin → fractional quantity, NOT rounded to a whole coin.
        when(brokerPort.getAvailableBalance(simulationId)).thenReturn(Money.usd(new BigDecimal("1000")));
        when(marketEventRepository.findByTickersAndTimeRange(any(), any(), any(), any(), eq(EventType.PRICE), anyInt()))
                .thenReturn(List.of(priceEvent("BTC", new BigDecimal("150"))));
        when(tradingLimitsConfig.crypto()).thenReturn(new BigDecimal("100"));

        when(brokerPort.executeTrade(any())).thenReturn(TradeResult.executed(
                OrderId.generate(), Money.usd(new BigDecimal("150")), new BigDecimal("6.6"), now));

        Portfolio portfolio = new Portfolio(simulationId, Map.of(), Money.usd(new BigDecimal("10")));
        when(brokerPort.getCurrentPortfolio(simulationId)).thenReturn(portfolio);
        when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExecutionResult result = service.execute(decision);

        assertTrue(result.tradeExecuted());
        ArgumentCaptor<ExecuteTradeCommand> cmd = ArgumentCaptor.forClass(ExecuteTradeCommand.class);
        verify(brokerPort).executeTrade(cmd.capture());
        BigDecimal q = cmd.getValue().quantity();
        // 1000 * 0.995 / 150 = 6.6333…  → scale-8 floor keeps the fractional component.
        assertTrue(q.compareTo(new BigDecimal("6")) > 0 && q.compareTo(new BigDecimal("7")) < 0,
                "fractional broker must keep a fractional quantity between 6 and 7, was: " + q);
        assertTrue(q.scale() > 0 || q.stripTrailingZeros().scale() > 0,
                "fractional quantity must carry a fractional component, was: " + q);
    }

    @Test
    @DisplayName("no-quantity BUY records on the decision that the SYSTEM chose the size")
    void defaultedToMaxBuyLeavesAnAuditNote() {
        // The capped-down branch has always written an audit note; the defaulted-to-max
        // branch — the one that silently turns "BUY" into "BUY everything permitted" —
        // wrote only a log line. That asymmetry is how a tranche strategy's first BUY
        // consumed its whole position limit with nothing on the decision explaining why.
        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        ConsensusDecision decision = new ConsensusDecision(
                simulationId, now, ConsensusMethod.MAJORITY, ConsensusActionParser.parse("BUY"));
        decision.setId(3L);
        decision.setSymbol(btc);
        decision.setInvestmentType(btc.type());

        when(brokerRouter.capabilitiesForSimulation(simulationId))
                .thenReturn(BrokerCapabilities.fractional());
        when(brokerPort.getAvailableBalance(simulationId)).thenReturn(Money.usd(new BigDecimal("1000")));
        when(marketEventRepository.findByTickersAndTimeRange(any(), any(), any(), any(), eq(EventType.PRICE), anyInt()))
                .thenReturn(List.of(priceEvent("BTC", new BigDecimal("150"))));
        when(tradingLimitsConfig.crypto()).thenReturn(new BigDecimal("100"));
        when(brokerPort.executeTrade(any())).thenReturn(TradeResult.executed(
                OrderId.generate(), Money.usd(new BigDecimal("150")), new BigDecimal("6.6"), now));
        Portfolio portfolio = new Portfolio(simulationId, Map.of(), Money.usd(new BigDecimal("10")));
        when(brokerPort.getCurrentPortfolio(simulationId)).thenReturn(portfolio);
        when(portfolioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.execute(decision);

        assertTrue(decision.getNotes() != null
                        && decision.getNotes().contains("No quantity in the consensus"),
                "the defaulted-to-max path must say who chose the size; notes were: "
                        + decision.getNotes());
        assertTrue(decision.getNotes().contains("explicit quantity"),
                "the note must state the fix (agents emit a quantity); notes were: "
                        + decision.getNotes());
    }
}
