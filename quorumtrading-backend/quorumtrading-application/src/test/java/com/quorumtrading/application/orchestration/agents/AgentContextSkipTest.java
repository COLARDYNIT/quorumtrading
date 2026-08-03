package com.quorumtrading.application.orchestration.agents;

import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.service.ModelResolutionService;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.MarketDataContext;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.marketdata.Timeframe;
import com.quorumtrading.model.orchestration.AgentContext;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Agent.hasContextForDecision() — verifies that agents without sufficient
 * news data are skipped during backtests but always participate in live simulations.
 */
@ExtendWith(MockitoExtension.class)
class AgentContextSkipTest {

    @Mock
    private LLMPort llmPort;

    private SentimentAgent sentimentAgent;
    private FundamentalAgent fundamentalAgent;
    private TechnicalAgent technicalAgent;

    @BeforeEach
    void setUp() {
        ModelResolutionService modelResolution = new ModelResolutionService();
        sentimentAgent = new SentimentAgent(llmPort, 0, modelResolution, null);
        fundamentalAgent = new FundamentalAgent(llmPort, 0, modelResolution, null);
        technicalAgent = new TechnicalAgent(llmPort, 0, modelResolution, null);
    }

    // === SentimentAgent: needs >= 3 news/social events ===

    @Test
    void sentimentAgent_backtestWithNoNews_shouldSkip() {
        AgentContext context = backtestContext("QQQ", List.of());
        assertThat(sentimentAgent.hasContextForDecision(context)).isFalse();
    }

    @Test
    void sentimentAgent_backtestWithTwoNewsEvents_shouldSkip() {
        List<MarketEvent> events = List.of(
            newsEvent("QQQ", "Headline 1"),
            newsEvent("QQQ", "Headline 2")
        );
        AgentContext context = backtestContext("QQQ", events);
        assertThat(sentimentAgent.hasContextForDecision(context)).isFalse();
    }

    @Test
    void sentimentAgent_backtestWithThreeNewsEvents_shouldParticipate() {
        List<MarketEvent> events = List.of(
            newsEvent("QQQ", "Headline 1"),
            newsEvent("QQQ", "Headline 2"),
            socialEvent("QQQ", "Reddit post")
        );
        AgentContext context = backtestContext("QQQ", events);
        assertThat(sentimentAgent.hasContextForDecision(context)).isTrue();
    }

    @Test
    void sentimentAgent_liveWithNoNews_shouldAlwaysParticipate() {
        AgentContext context = liveContext("QQQ", List.of());
        assertThat(sentimentAgent.hasContextForDecision(context)).isTrue();
    }

    // === FundamentalAgent: needs >= 2 news events ===

    @Test
    void fundamentalAgent_backtestWithNoNews_shouldSkip() {
        AgentContext context = backtestContext("QQQ", List.of());
        assertThat(fundamentalAgent.hasContextForDecision(context)).isFalse();
    }

    @Test
    void fundamentalAgent_backtestWithOneNewsEvent_shouldSkip() {
        List<MarketEvent> events = List.of(
            newsEvent("QQQ", "Single headline")
        );
        AgentContext context = backtestContext("QQQ", events);
        assertThat(fundamentalAgent.hasContextForDecision(context)).isFalse();
    }

    @Test
    void fundamentalAgent_backtestWithTwoNewsEvents_shouldParticipate() {
        List<MarketEvent> events = List.of(
            newsEvent("QQQ", "Headline 1"),
            newsEvent("QQQ", "Headline 2")
        );
        AgentContext context = backtestContext("QQQ", events);
        assertThat(fundamentalAgent.hasContextForDecision(context)).isTrue();
    }

    @Test
    void fundamentalAgent_liveWithNoNews_shouldAlwaysParticipate() {
        AgentContext context = liveContext("QQQ", List.of());
        assertThat(fundamentalAgent.hasContextForDecision(context)).isTrue();
    }

    // === TechnicalAgent: always participates (default) ===

    @Test
    void technicalAgent_backtestWithNoNews_shouldAlwaysParticipate() {
        AgentContext context = backtestContext("QQQ", List.of());
        assertThat(technicalAgent.hasContextForDecision(context)).isTrue();
    }

    // === News for wrong symbol should not count ===

    @Test
    void sentimentAgent_backtestWithNewsForDifferentSymbol_shouldSkip() {
        List<MarketEvent> events = List.of(
            newsEvent("AAPL", "Apple news 1"),
            newsEvent("AAPL", "Apple news 2"),
            newsEvent("AAPL", "Apple news 3")
        );
        AgentContext context = backtestContext("QQQ", events);
        assertThat(sentimentAgent.hasContextForDecision(context)).isFalse();
    }

    // === Skip reasons are explained, not just flagged ===

    @Test
    void sentimentAgent_skipReason_namesTheShortfall() {
        AgentContext context = backtestContext("QQQ", List.of(newsEvent("QQQ", "Only one")));

        Agent.ContextCheck check = sentimentAgent.checkContext(context);

        assertThat(check.sufficient()).isFalse();
        assertThat(check.reason()).contains("1 of 3");
    }

    @Test
    void fundamentalAgent_skipReason_namesTheShortfall() {
        AgentContext context = backtestContext("QQQ", List.of());

        Agent.ContextCheck check = fundamentalAgent.checkContext(context);

        assertThat(check.sufficient()).isFalse();
        assertThat(check.reason()).contains("0 of 2");
    }

    @Test
    void sufficientContext_carriesNoReason() {
        AgentContext context = backtestContext("QQQ", List.of());

        Agent.ContextCheck check = technicalAgent.checkContext(context);

        assertThat(check.sufficient()).isTrue();
        assertThat(check.reason()).isNull();
    }

    // === Helpers ===

    private AgentContext backtestContext(String ticker, List<MarketEvent> events) {
        return buildContext(ticker, events, false);
    }

    private AgentContext liveContext(String ticker, List<MarketEvent> events) {
        return buildContext(ticker, events, true);
    }

    private AgentContext buildContext(String ticker, List<MarketEvent> events, boolean live) {
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        Portfolio portfolio = new Portfolio(simulationId, Map.of(),
            new Money(new BigDecimal("10000"), Money.Currency.USD));
        StrategyConfig strategyConfig = new StrategyConfig();
        Instant timestamp = Instant.now();

        MarketDataContext marketData;
        if (events.isEmpty()) {
            marketData = MarketDataContext.empty(timestamp);
        } else {
            Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
                Timeframe.LAST_24H, events
            );
            Set<String> symbols = new HashSet<>();
            symbols.add(ticker);
            events.forEach(e -> symbols.add(e.symbol()));
            marketData = MarketDataContext.from(eventsByTimeframe, symbols, timestamp);
        }

        return new AgentContext(
            simulationId, ticker, portfolio, marketData,
            List.of(), Map.of(), timestamp, strategyConfig, null,
            null, Map.of(), null, null, null, null, null, null, live, null
        );
    }

    private MarketEvent newsEvent(String symbol, String headline) {
        return new MarketEvent(
            Instant.now(), EventType.NEWS, "test-source", symbol,
            Map.of("headline", headline)
        );
    }

    private MarketEvent socialEvent(String symbol, String title) {
        return new MarketEvent(
            Instant.now(), EventType.SOCIAL, "reddit", symbol,
            Map.of("title", title)
        );
    }
}
