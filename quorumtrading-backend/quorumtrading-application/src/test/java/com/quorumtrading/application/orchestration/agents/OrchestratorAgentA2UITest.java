package com.quorumtrading.application.orchestration.agents;

import com.quorumtrading.application.config.TradingLimitsConfig;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.service.ModelResolutionService;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.enums.RecommendationType;
import com.quorumtrading.model.marketdata.MarketDataContext;
import com.quorumtrading.model.orchestration.AgentContext;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.Percentage;
import com.quorumtrading.model.valueobject.SimulationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for OrchestratorAgent A2UI payload extraction from LLM responses.
 */
@ExtendWith(MockitoExtension.class)
class OrchestratorAgentA2UITest {

    @Mock
    private LLMPort llmPort;

    @Mock
    private TradingLimitsConfig tradingLimitsConfig;

    private OrchestratorAgent createAgent(List<LLMDecision> agentDecisions) {
        return new OrchestratorAgent(llmPort, agentDecisions, tradingLimitsConfig, new ModelResolutionService(), true, null);
    }

    private AgentContext createContext() {
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        Portfolio portfolio = new Portfolio(simulationId, Map.of(), new Money(new BigDecimal("10000"), Money.Currency.USD));
        StrategyConfig strategyConfig = new StrategyConfig();
        // A model must resolve at some tier: an agent now refuses to run rather than
        // sending a null model and letting the engine pick silently.
        strategyConfig.setLlmModel("test-model");
        Instant timestamp = Instant.now();
        MarketDataContext marketData = MarketDataContext.empty(timestamp);

        return new AgentContext(
            simulationId,
            "BTC",
            portfolio,
            marketData,
            List.of(),
            Map.of(),
            timestamp,
            strategyConfig,
            null
        );
    }

    @Test
    void shouldExtractA2UIPayloadFromResponse() {
        // Given: LLM response with A2UI block
        String a2uiPayload = "{\"surfaceUpdate\":{\"components\":[{\"id\":\"c1\",\"type\":\"ConfidenceGauge\",\"properties\":{\"value\":{\"literalNumber\":85}}}]}}";
        String llmResponse = "{\"action\":\"BUY\",\"symbol\":\"BTC\",\"quantity\":0.5,\"confidence\":0.85,\"reasoning\":\"Strong signals\"}\n"
            + "<a2ui>" + a2uiPayload + "</a2ui>";

        when(llmPort.queryWithUsage(anyString(), any()))
            .thenReturn(LLMQueryResult.of(llmResponse, LLMUsage.ZERO, "test-model"));

        OrchestratorAgent agent = createAgent(List.of());
        AgentContext context = createContext();

        // When
        LLMDecision decision = agent.analyze(context);

        // Then
        assertThat(decision).isNotNull();
        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.BUY);
        assertThat(decision.getUiPayload()).isNotNull();
        assertThat(decision.getUiPayload()).isEqualTo(a2uiPayload);
    }

    @Test
    void shouldReturnNullUiPayloadWhenNoA2UIBlock() {
        // Given: LLM response without A2UI block
        String llmResponse = "{\"action\":\"HOLD\",\"symbol\":\"BTC\",\"quantity\":0.0,\"confidence\":0.6,\"reasoning\":\"No clear signal\"}";

        when(llmPort.queryWithUsage(anyString(), any()))
            .thenReturn(LLMQueryResult.of(llmResponse, LLMUsage.ZERO, "test-model"));

        OrchestratorAgent agent = createAgent(List.of());
        AgentContext context = createContext();

        // When
        LLMDecision decision = agent.analyze(context);

        // Then
        assertThat(decision).isNotNull();
        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.HOLD);
        assertThat(decision.getUiPayload()).isNull();
    }

    @Test
    void shouldHandleMalformedA2UITags() {
        // Given: LLM response with unclosed A2UI tag
        String llmResponse = "{\"action\":\"SELL\",\"symbol\":\"BTC\",\"quantity\":0.1,\"confidence\":0.7,\"reasoning\":\"Bearish\"}\n"
            + "<a2ui>{\"surfaceUpdate\":{}}";

        when(llmPort.queryWithUsage(anyString(), any()))
            .thenReturn(LLMQueryResult.of(llmResponse, LLMUsage.ZERO, "test-model"));

        OrchestratorAgent agent = createAgent(List.of());
        AgentContext context = createContext();

        // When
        LLMDecision decision = agent.analyze(context);

        // Then: Decision should parse, but uiPayload should be null (unclosed tag)
        assertThat(decision).isNotNull();
        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.SELL);
        assertThat(decision.getUiPayload()).isNull();
    }

    @Test
    void shouldHandleEmptyA2UIBlock() {
        // Given: LLM response with empty A2UI block
        String llmResponse = "{\"action\":\"HOLD\",\"symbol\":\"BTC\",\"quantity\":0.0,\"confidence\":0.5,\"reasoning\":\"Waiting\"}\n"
            + "<a2ui>  </a2ui>";

        when(llmPort.queryWithUsage(anyString(), any()))
            .thenReturn(LLMQueryResult.of(llmResponse, LLMUsage.ZERO, "test-model"));

        OrchestratorAgent agent = createAgent(List.of());
        AgentContext context = createContext();

        // When
        LLMDecision decision = agent.analyze(context);

        // Then: uiPayload should be null for empty block
        assertThat(decision).isNotNull();
        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.HOLD);
        assertThat(decision.getUiPayload()).isNull();
    }

    @Test
    void shouldNotConfuseA2UIBlockWithJSONParsing() {
        // Given: LLM response where A2UI is before the JSON (edge case)
        String a2uiPayload = "{\"surfaceUpdate\":{\"components\":[]}}";
        String llmResponse = "<a2ui>" + a2uiPayload + "</a2ui>\n"
            + "{\"action\":\"BUY\",\"symbol\":\"BTC\",\"quantity\":1.0,\"confidence\":0.9,\"reasoning\":\"All in\"}";

        when(llmPort.queryWithUsage(anyString(), any()))
            .thenReturn(LLMQueryResult.of(llmResponse, LLMUsage.ZERO, "test-model"));

        OrchestratorAgent agent = createAgent(List.of());
        AgentContext context = createContext();

        // When
        LLMDecision decision = agent.analyze(context);

        // Then: A2UI is extracted and JSON still parses correctly
        assertThat(decision).isNotNull();
        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.BUY);
        assertThat(decision.getUiPayload()).isEqualTo(a2uiPayload);
    }
}
