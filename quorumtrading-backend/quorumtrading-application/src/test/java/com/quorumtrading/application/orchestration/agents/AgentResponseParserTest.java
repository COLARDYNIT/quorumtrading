package com.quorumtrading.application.orchestration.agents;

import com.quorumtrading.model.entity.LLMDecision;
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

import static org.assertj.core.api.Assertions.assertThat;

class AgentResponseParserTest {

    // --- stripMarkdownCodeBlocks ---

    @Test
    void stripMarkdownCodeBlocks_shouldReturnNullForNull() {
        assertThat(AgentResponseParser.stripMarkdownCodeBlocks(null)).isNull();
    }

    @Test
    void stripMarkdownCodeBlocks_shouldReturnPlainJsonUnchanged() {
        String json = "{\"action\":\"BUY\",\"symbol\":\"BTC\",\"quantity\":0.1,\"confidence\":0.8,\"reasoning\":\"Test\"}";
        assertThat(AgentResponseParser.stripMarkdownCodeBlocks(json)).isEqualTo(json);
    }

    @Test
    void stripMarkdownCodeBlocks_shouldStripJsonCodeFence() {
        String wrapped = "```json\n{\"action\":\"HOLD\"}\n```";
        assertThat(AgentResponseParser.stripMarkdownCodeBlocks(wrapped)).isEqualTo("{\"action\":\"HOLD\"}");
    }

    @Test
    void stripMarkdownCodeBlocks_shouldStripPlainCodeFence() {
        String wrapped = "```\n{\"action\":\"SELL\"}\n```";
        assertThat(AgentResponseParser.stripMarkdownCodeBlocks(wrapped)).isEqualTo("{\"action\":\"SELL\"}");
    }

    @Test
    void stripMarkdownCodeBlocks_shouldTrimWhitespace() {
        String wrapped = "  ```json\n  {\"action\":\"BUY\"}  \n```  ";
        assertThat(AgentResponseParser.stripMarkdownCodeBlocks(wrapped)).isEqualTo("{\"action\":\"BUY\"}");
    }

    // --- stripJsonComments ---

    @Test
    void stripJsonComments_shouldReturnNullForNull() {
        assertThat(AgentResponseParser.stripJsonComments(null)).isNull();
    }

    @Test
    void stripJsonComments_shouldPassThroughCleanJson() {
        String json = "{\"action\":\"BUY\",\"confidence\":0.8}";
        assertThat(AgentResponseParser.stripJsonComments(json)).isEqualTo(json);
    }

    @Test
    void stripJsonComments_shouldStripSingleLineComments() {
        String input = """
                {
                  "action": "BUY", // this is a buy signal
                  "confidence": 0.8 // high confidence
                }""";
        String result = AgentResponseParser.stripJsonComments(input);
        assertThat(result).doesNotContain("//");
        assertThat(result).contains("\"action\": \"BUY\",");
        assertThat(result).contains("\"confidence\": 0.8");
    }

    @Test
    void stripJsonComments_shouldStripBlockComments() {
        String input = """
                {
                  /* agent reasoning */
                  "action": "SELL",
                  "confidence": /* inline */ 0.7
                }""";
        String result = AgentResponseParser.stripJsonComments(input);
        assertThat(result).doesNotContain("/*");
        assertThat(result).doesNotContain("*/");
        assertThat(result).contains("\"action\": \"SELL\",");
        assertThat(result).contains("0.7");
    }

    @Test
    void stripJsonComments_shouldPreserveCommentLikeSequencesInsideStrings() {
        String input = "{\"reasoning\":\"Price crossed // moving avg /* test */\",\"action\":\"BUY\",\"confidence\":0.5}";
        assertThat(AgentResponseParser.stripJsonComments(input)).isEqualTo(input);
    }

    @Test
    void stripJsonComments_shouldHandleEscapedQuotesInsideStrings() {
        String input = "{\"reasoning\":\"He said \\\"// not a comment\\\"\",\"action\":\"HOLD\",\"confidence\":0.6}";
        assertThat(AgentResponseParser.stripJsonComments(input)).isEqualTo(input);
    }

    @Test
    void stripJsonComments_shouldStripTrailingCommas() {
        String input = "{\"action\":\"BUY\",\"items\":[1,2,3,],\"confidence\":0.8,}";
        String result = AgentResponseParser.stripJsonComments(input);
        assertThat(result).isEqualTo("{\"action\":\"BUY\",\"items\":[1,2,3],\"confidence\":0.8}");
    }

    @Test
    void parseStandardResponse_shouldParseJsonWithComments() {
        String response = """
                {
                  "action": "BUY", // strong signal
                  "symbol": "AAPL",
                  "quantity": 10,
                  "confidence": 0.9,
                  "reasoning": "Bullish crossover"
                }""";
        AgentContext context = createContext("AAPL");

        LLMDecision decision = AgentResponseParser.parseStandardResponse(
                response, 100, context, "test-prompt", "gpt-4", "TECHNICAL");

        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.BUY);
        assertThat(decision.getConfidenceScore().value()).isEqualByComparingTo(new BigDecimal("0.9"));
    }

    // --- parseStandardResponse ---

    @Test
    void parseStandardResponse_shouldParseValidJson() {
        String response = "{\"action\":\"BUY\",\"symbol\":\"AAPL\",\"quantity\":10,\"confidence\":0.85,\"reasoning\":\"Strong uptrend\"}";
        AgentContext context = createContext("AAPL");

        LLMDecision decision = AgentResponseParser.parseStandardResponse(
                response, 150, context, "test-prompt", "gpt-4", "TECHNICAL");

        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.BUY);
        assertThat(decision.getConfidenceScore().value()).isEqualByComparingTo(new BigDecimal("0.85"));
        assertThat(decision.getReasoning()).isEqualTo("Strong uptrend");
        assertThat(decision.getTicker()).isEqualTo("AAPL");
        assertThat(decision.getRole()).isEqualTo("TECHNICAL");
        assertThat(decision.getExecutionTimeMs()).isEqualTo(150);
    }

    @Test
    void parseStandardResponse_shouldParseMarkdownWrappedJson() {
        String response = "```json\n{\"action\":\"SELL\",\"symbol\":\"BTC\",\"quantity\":0.5,\"confidence\":0.7,\"reasoning\":\"Bearish\"}\n```";
        AgentContext context = createContext("BTC");

        LLMDecision decision = AgentResponseParser.parseStandardResponse(
                response, 200, context, "test-prompt", "claude-3", "FUNDAMENTAL");

        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.SELL);
        assertThat(decision.getTicker()).isEqualTo("BTC");
    }

    @Test
    void parseStandardResponse_shouldReturnSkipOnGarbageInput() {
        String response = "This is not JSON at all";
        AgentContext context = createContext("ETH");

        LLMDecision decision = AgentResponseParser.parseStandardResponse(
                response, 100, context, "test-prompt", "gpt-4", "SENTIMENT");

        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.SKIP);
        assertThat(decision.getConfidenceScore().value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(decision.getTicker()).isEqualTo("ETH");
        assertThat(decision.getRole()).isEqualTo("SENTIMENT");
    }

    @Test
    void parseStandardResponse_shouldFallbackToContextTickerWhenSymbolMissing() {
        String response = "{\"action\":\"HOLD\",\"quantity\":0,\"confidence\":0.6,\"reasoning\":\"No clear signal\"}";
        AgentContext context = createContext("TSLA");

        LLMDecision decision = AgentResponseParser.parseStandardResponse(
                response, 120, context, "test-prompt", "gpt-4", "SENTIMENT");

        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.HOLD);
        assertThat(decision.getTicker()).isEqualTo("TSLA");
    }

    @Test
    void parseStandardResponse_shouldFallbackToContextTickerWhenSymbolIsNull() {
        String response = "{\"action\":\"BUY\",\"symbol\":null,\"quantity\":1,\"confidence\":0.9,\"reasoning\":\"Bullish\"}";
        AgentContext context = createContext("GOOG");

        LLMDecision decision = AgentResponseParser.parseStandardResponse(
                response, 80, context, "test-prompt", "gpt-4", "TECHNICAL");

        assertThat(decision.getTicker()).isEqualTo("GOOG");
    }

    @Test
    void parseStandardResponse_shouldExtractJsonFromPreambleText() {
        String response = "Here is my analysis based on the market data:\n{\"action\":\"BUY\",\"symbol\":\"AAPL\",\"quantity\":5,\"confidence\":0.75,\"reasoning\":\"Bullish signal\"}";
        AgentContext context = createContext("AAPL");

        LLMDecision decision = AgentResponseParser.parseStandardResponse(
                response, 100, context, "test-prompt", "gpt-4", "TECHNICAL");

        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.BUY);
        assertThat(decision.getConfidenceScore().value()).isEqualByComparingTo(new BigDecimal("0.75"));
        assertThat(decision.getTicker()).isEqualTo("AAPL");
    }

    @Test
    void parseStandardResponse_shouldHandleLowercaseAction() {
        String response = "{\"action\":\"buy\",\"symbol\":\"MSFT\",\"quantity\":3,\"confidence\":0.8,\"reasoning\":\"Momentum play\"}";
        AgentContext context = createContext("MSFT");

        LLMDecision decision = AgentResponseParser.parseStandardResponse(
                response, 90, context, "test-prompt", "claude-3", "FUNDAMENTAL");

        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.BUY);
        assertThat(decision.getTicker()).isEqualTo("MSFT");
    }

    @Test
    void parseStandardResponse_shouldHandleMixedCaseAction() {
        String response = "{\"action\":\"Hold\",\"symbol\":\"SPY\",\"quantity\":0,\"confidence\":0.6,\"reasoning\":\"Uncertain\"}";
        AgentContext context = createContext("SPY");

        LLMDecision decision = AgentResponseParser.parseStandardResponse(
                response, 110, context, "test-prompt", "gpt-4", "SENTIMENT");

        assertThat(decision.getRecommendation()).isEqualTo(RecommendationType.HOLD);
    }

    // --- helpers ---

    private AgentContext createContext(String ticker) {
        SimulationId simulationId = new SimulationId(UUID.randomUUID());
        Portfolio portfolio = new Portfolio(simulationId, Map.of(), new Money(new BigDecimal("10000"), Money.Currency.USD));
        StrategyConfig strategyConfig = new StrategyConfig();
        Instant timestamp = Instant.now();
        MarketDataContext marketData = MarketDataContext.empty(timestamp);

        return new AgentContext(
                simulationId,
                ticker,
                portfolio,
                marketData,
                List.of(),
                Map.of(),
                timestamp,
                strategyConfig,
                null
        );
    }
}
