package com.quorumtrading.application.orchestration.agents;

import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.service.ModelResolutionService;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.RecommendationType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.orchestration.AgentContext;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.marketdata.MarketDataContext;
import com.quorumtrading.model.marketdata.Timeframe;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for FundamentalAgent to ensure market events and news are properly included in prompts.
 */
@ExtendWith(MockitoExtension.class)
class FundamentalAgentTest {

    @Mock
    private LLMPort llmPort;

    private FundamentalAgent agent;

    @BeforeEach
    void setUp() {
        agent = new FundamentalAgent(llmPort, 60, new ModelResolutionService(), null); // 60 minutes TTL for tests
    }

    @Test
    void shouldIncludeMarketEventsInPrompt() {
        // Given: Market events with price data
        List<MarketEvent> events = List.of(
            createPriceEvent("BTC", 45000.0, Instant.parse("2025-01-01T12:00:00Z"))
        );

        AgentContext context = createContext(events);

        // Mock LLM response
        String llmResponse = "{\"action\":\"HOLD\",\"symbol\":\"BTC\",\"quantity\":0.0,\"confidence\":0.6,\"reasoning\":\"Test\"}";
        when(llmPort.queryWithUsage(anyString(), any())).thenReturn(LLMQueryResult.of(llmResponse, LLMUsage.ZERO, "test-model"));

        // When: Agent analyzes
        agent.analyze(context);

        // Then: Prompt should contain market price information
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmPort).queryWithUsage(promptCaptor.capture(), any());

        String capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt).contains("CURRENT MARKET PRICES");
        assertThat(capturedPrompt).contains("BTC");
        assertThat(capturedPrompt).contains("45000");
    }

    @Test
    void shouldIncludeNewsEventsInPrompt() {
        // Given: Market events with news
        List<MarketEvent> events = List.of(
            createNewsEvent("BTC", "Bitcoin reaches new high", "CoinDesk", Instant.parse("2025-01-01T12:00:00Z")),
            createRedditEvent("BTC", "BTC discussion trending", "r/cryptocurrency", Instant.parse("2025-01-01T11:00:00Z"))
        );

        AgentContext context = createContext(events);

        // Mock LLM response
        String llmResponse = "{\"action\":\"BUY\",\"symbol\":\"BTC\",\"quantity\":0.1,\"confidence\":0.8,\"reasoning\":\"Positive news\"}";
        when(llmPort.queryWithUsage(anyString(), any())).thenReturn(LLMQueryResult.of(llmResponse, LLMUsage.ZERO, "test-model"));

        // When: Agent analyzes
        agent.analyze(context);

        // Then: Prompt should contain news information
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmPort).queryWithUsage(promptCaptor.capture(), any());

        String capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt).contains("RECENT NEWS & MARKET INFORMATION");
        assertThat(capturedPrompt).contains("Bitcoin reaches new high");
        assertThat(capturedPrompt).contains("CoinDesk");
        assertThat(capturedPrompt).contains("BTC discussion trending");
        assertThat(capturedPrompt).contains("r/cryptocurrency");
    }

    @Test
    void shouldIncludeBothPriceAndNewsInPrompt() {
        // Given: Market events with both price and news
        List<MarketEvent> events = List.of(
            createPriceEvent("BTC", 50000.0, Instant.parse("2025-01-01T12:00:00Z")),
            createNewsEvent("BTC", "Major adoption news", "Bloomberg", Instant.parse("2025-01-01T11:30:00Z"))
        );

        AgentContext context = createContext(events);

        // Mock LLM response
        String llmResponse = "{\"action\":\"BUY\",\"symbol\":\"BTC\",\"quantity\":0.2,\"confidence\":0.85,\"reasoning\":\"Strong fundamentals\"}";
        when(llmPort.queryWithUsage(anyString(), any())).thenReturn(LLMQueryResult.of(llmResponse, LLMUsage.ZERO, "test-model"));

        // When: Agent analyzes
        agent.analyze(context);

        // Then: Prompt should contain both price and news
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmPort).queryWithUsage(promptCaptor.capture(), any());

        String capturedPrompt = promptCaptor.getValue();

        // Verify price data
        assertThat(capturedPrompt).contains("CURRENT MARKET PRICES");
        assertThat(capturedPrompt).contains("50000");

        // Verify news data
        assertThat(capturedPrompt).contains("RECENT NEWS & MARKET INFORMATION");
        assertThat(capturedPrompt).contains("Major adoption news");
        assertThat(capturedPrompt).contains("Bloomberg");
    }

    @Test
    void shouldIncludeMultiTimeframeDataInPrompt() {
        // Given: Price events distributed across multiple timeframes
        Instant now = Instant.parse("2025-01-15T12:00:00Z");

        // 24h data: prices trending up from 44000 to 45000
        List<MarketEvent> last24h = List.of(
            createPriceEvent("BTC", 44000.0, now.minusSeconds(3600 * 20)),
            createPriceEvent("BTC", 44500.0, now.minusSeconds(3600 * 10)),
            createPriceEvent("BTC", 45000.0, now.minusSeconds(3600))
        );

        // 7d data: wider range showing weekly trend
        List<MarketEvent> last7d = List.of(
            createPriceEvent("BTC", 42000.0, now.minusSeconds(86400 * 6)),
            createPriceEvent("BTC", 43000.0, now.minusSeconds(86400 * 3)),
            createPriceEvent("BTC", 45000.0, now.minusSeconds(86400))
        );

        Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
            Timeframe.LAST_24H, last24h,
            Timeframe.LAST_7D, last7d
        );

        Set<String> symbols = Set.of("BTC");
        MarketDataContext marketData = MarketDataContext.from(eventsByTimeframe, symbols, now);

        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        Portfolio portfolio = new Portfolio(simulationId, Map.of(), new Money(new BigDecimal("10000"), Money.Currency.USD));
        StrategyConfig strategyConfig = new StrategyConfig();
        // A model must resolve at some tier: an agent now refuses to run rather than
        // sending a null model and letting the engine pick silently.
        strategyConfig.setLlmModel("test-model");

        AgentContext context = new AgentContext(
            simulationId, "BTC", portfolio, marketData,
            List.of(), Map.of(), now, strategyConfig, null
        );

        // Mock LLM response
        String llmResponse = "{\"action\":\"HOLD\",\"symbol\":\"BTC\",\"quantity\":0.0,\"confidence\":0.7,\"reasoning\":\"Timeframe analysis\"}";
        when(llmPort.queryWithUsage(anyString(), any())).thenReturn(LLMQueryResult.of(llmResponse, LLMUsage.ZERO, "test-model"));

        // When: Agent analyzes
        agent.analyze(context);

        // Then: Prompt should contain multi-timeframe trajectory section
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmPort).queryWithUsage(promptCaptor.capture(), any());

        String capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt).contains("MARKET TRAJECTORY & STABILITY");
        assertThat(capturedPrompt).contains("Price Trajectory Summary");
        assertThat(capturedPrompt).contains("24h:");
        assertThat(capturedPrompt).contains("UP trend");
        assertThat(capturedPrompt).contains("Fundamental Interpretation Hints");
    }

    @Test
    void shouldHandleEmptyNewsGracefully() {
        // Given: Only price events, no news
        List<MarketEvent> events = List.of(
            createPriceEvent("BTC", 40000.0, Instant.parse("2025-01-01T12:00:00Z"))
        );

        AgentContext context = createContext(events);

        // Mock LLM response
        String llmResponse = "{\"action\":\"HOLD\",\"symbol\":\"BTC\",\"quantity\":0.0,\"confidence\":0.5,\"reasoning\":\"No news\"}";
        when(llmPort.queryWithUsage(anyString(), any())).thenReturn(LLMQueryResult.of(llmResponse, LLMUsage.ZERO, "test-model"));

        // When: Agent analyzes
        agent.analyze(context);

        // Then: Prompt should indicate no news available
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmPort).queryWithUsage(promptCaptor.capture(), any());

        String capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt).contains("No recent news or market information available");
    }

    @Test
    void shouldReturnValidLLMDecisionWithCorrectRole() {
        // Given: Basic context with no events
        AgentContext context = createContext(List.of());

        // Mock LLM response
        String llmResponse = "{\"action\":\"HOLD\",\"symbol\":\"BTC\",\"quantity\":0.0,\"confidence\":0.6,\"reasoning\":\"Test\"}";
        when(llmPort.queryWithUsage(anyString(), any())).thenReturn(LLMQueryResult.of(llmResponse, LLMUsage.ZERO, "test-model"));

        // When: Agent analyzes
        LLMDecision decision = agent.analyze(context);

        // Then: Decision should be valid
        assertThat(decision).isNotNull();
        assertThat(decision.getRole()).isEqualTo("FUNDAMENTAL");
        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.HOLD);
    }

    // Helper methods to create test data

    private AgentContext createContext(List<MarketEvent> events) {
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        Portfolio portfolio = new Portfolio(simulationId, Map.of(), new Money(new BigDecimal("10000"), Money.Currency.USD));
        StrategyConfig strategyConfig = new StrategyConfig();
        // A model must resolve at some tier: an agent now refuses to run rather than
        // sending a null model and letting the engine pick silently.
        strategyConfig.setLlmModel("test-model");
        Instant timestamp = Instant.now();

        // Build MarketDataContext from events
        MarketDataContext marketData;
        if (events.isEmpty()) {
            marketData = MarketDataContext.empty(timestamp);
        } else {
            // Group events by timeframe (put all in LAST_24H for simplicity)
            Map<Timeframe, List<MarketEvent>> eventsByTimeframe = Map.of(
                Timeframe.LAST_24H, events
            );
            Set<String> symbols = Set.of("BTC");
            marketData = MarketDataContext.from(eventsByTimeframe, symbols, timestamp);
        }

        return new AgentContext(
            simulationId,
            "BTC",
            portfolio,
            marketData,
            List.of(),
            Map.of(),
            timestamp,
            strategyConfig,
            null  // indicators - not needed for this test
        );
    }

    private MarketEvent createPriceEvent(String symbol, double price, Instant timestamp) {
        Map<String, Object> rawData = Map.of("price", price);
        return new MarketEvent(
            timestamp,
            EventType.PRICE,
            "test-source",
            symbol,
            rawData
        );
    }

    private MarketEvent createNewsEvent(String symbol, String headline, String source, Instant timestamp) {
        Map<String, Object> rawData = Map.of("headline", headline);
        return new MarketEvent(
            timestamp,
            EventType.NEWS,
            source,
            symbol,
            rawData
        );
    }

    private MarketEvent createRedditEvent(String symbol, String title, String source, Instant timestamp) {
        Map<String, Object> rawData = Map.of("title", title);
        return new MarketEvent(
            timestamp,
            EventType.SOCIAL,
            source,
            symbol,
            rawData
        );
    }
}
