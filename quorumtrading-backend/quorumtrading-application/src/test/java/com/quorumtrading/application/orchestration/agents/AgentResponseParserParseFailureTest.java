package com.quorumtrading.application.orchestration.agents;

import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.entity.LlmParseFailure;
import com.quorumtrading.model.entity.Portfolio;
import com.quorumtrading.model.enums.RecommendationType;
import com.quorumtrading.model.marketdata.MarketDataContext;
import com.quorumtrading.model.orchestration.AgentContext;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AgentResponseParserParseFailureTest {

    private AgentContext createContext() {
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        Portfolio portfolio = new Portfolio(simulationId, Map.of(), new Money(new BigDecimal("10000"), Money.Currency.USD));
        StrategyConfig strategyConfig = new StrategyConfig();
        Instant timestamp = Instant.parse("2025-06-04T14:30:00Z");
        MarketDataContext marketData = MarketDataContext.empty(timestamp);

        return new AgentContext(
            simulationId, "QQQ", portfolio, marketData,
            List.of(), Map.of(), timestamp, strategyConfig, null
        );
    }

    @Test
    void parseFailure_invokesConsumerWithCorrectDetails() {
        // Given: unparseable LLM response
        String rawResponse = "I cannot provide trading advice due to ethical concerns.";
        AgentContext context = createContext();
        AtomicReference<LlmParseFailure> captured = new AtomicReference<>();

        // When
        LLMDecision decision = AgentResponseParser.parseStandardResponse(
            rawResponse, 1234, context, "test prompt", "meta-llama/llama-4-scout",
            "TECHNICAL", captured::set);

        // Then: consumer was invoked with failure details
        assertThat(captured.get()).isNotNull();
        LlmParseFailure failure = captured.get();
        assertThat(failure.simulationId()).isEqualTo(context.getSimulationId());
        assertThat(failure.decisionTimestamp()).isEqualTo(context.getTimestamp());
        assertThat(failure.ticker()).isEqualTo("QQQ");
        assertThat(failure.agentRole()).isEqualTo("TECHNICAL");
        assertThat(failure.actualModel()).isEqualTo("meta-llama/llama-4-scout");
        assertThat(failure.executionTimeMs()).isEqualTo(1234);
        assertThat(failure.prompt()).isEqualTo("test prompt");
        assertThat(failure.rawResponse()).isEqualTo(rawResponse);
        assertThat(failure.errorMessage()).isNotNull();
        assertThat(failure.errorType()).isNotNull();

        // And the SKIP decision is still returned
        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.SKIP);
    }

    @Test
    void parseFailure_consumerExceptionDoesNotBreakSkipDecision() {
        // Given: consumer that throws
        String rawResponse = "not json at all";
        AgentContext context = createContext();

        // When: consumer throws RuntimeException
        LLMDecision decision = AgentResponseParser.parseStandardResponse(
            rawResponse, 500, context, "prompt", "test-model", "FUNDAMENTAL",
            failure -> { throw new RuntimeException("DB connection failed"); });

        // Then: SKIP decision is still returned despite consumer failure
        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.SKIP);
        assertThat(decision.getTicker()).isEqualTo("QQQ");
    }

    @Test
    void nullConsumer_worksWithoutError() {
        // Given: null consumer (backward compat)
        String rawResponse = "invalid json";
        AgentContext context = createContext();

        // When
        LLMDecision decision = AgentResponseParser.parseStandardResponse(
            rawResponse, 100, context, "prompt", "model", "SENTIMENT", null);

        // Then: works fine, returns SKIP
        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.SKIP);
    }

    @Test
    void successfulParse_doesNotInvokeConsumer() {
        // Given: valid JSON response
        String validResponse = "{\"action\":\"BUY\",\"symbol\":\"QQQ\",\"confidence\":0.85,\"reasoning\":\"Strong signals\"}";
        AgentContext context = createContext();
        AtomicReference<LlmParseFailure> captured = new AtomicReference<>();

        // When
        LLMDecision decision = AgentResponseParser.parseStandardResponse(
            validResponse, 200, context, "prompt", "test-model", "TECHNICAL",
            captured::set);

        // Then: consumer was NOT invoked
        assertThat(captured.get()).isNull();
        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.BUY);
    }
}
