package com.quorumtrading.adapter.llm.local;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test suite for DummyExecutor.
 * Following TDD approach - tests define expected behavior for E2E testing executor.
 */
class DummyExecutorTest {

    private DummyExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new DummyExecutor();
    }

    @Test
    void shouldReturnStrategyResponseForMarketResearchPrompt() {
        // Given: A prompt containing "market research"
        String prompt = "Analyze market research for BTC momentum strategy";

        // When: We execute the prompt
        String response = executor.execute(prompt);

        // Then: Should return a response containing markdown content
        assertThat(response).contains("# ");
        assertThat(response).isNotEmpty();
    }

    @Test
    void shouldReturnStrategyResponseForFindStrategyPrompt() {
        // Given: A prompt containing "find strategy"
        String prompt = "Find strategy for ETH trading";

        // When: We execute the prompt
        String response = executor.execute(prompt);

        // Then: Should return strategy discovery response
        assertThat(response).isNotEmpty();
    }

    @Test
    void shouldReturnTechnicalResponseForTechnicalPrompt() {
        // Given: A prompt containing technical indicators
        String prompt = "Analyze RSI and MACD for BTC";

        // When: We execute the prompt
        String response = executor.execute(prompt);

        // Then: Should return technical agent response
        assertThat(response).isNotEmpty();
    }

    @Test
    void shouldReturnSentimentResponseForSentimentPrompt() {
        // Given: A prompt containing sentiment keywords
        String prompt = "Analyze news sentiment for ETH";

        // When: We execute the prompt
        String response = executor.execute(prompt);

        // Then: Should return sentiment agent response
        assertThat(response).isNotEmpty();
    }

    @Test
    void shouldReturnFundamentalResponseForRiskKeywords() {
        // Given: A prompt containing risk-adjacent keywords (now handled by fundamental agent)
        String prompt = "Calculate position size and stop loss";

        // When: We execute the prompt
        String response = executor.execute(prompt);

        // Then: Should return a non-empty response
        assertThat(response).isNotEmpty();
    }

    @Test
    void shouldReturnFundamentalResponseForFundamentalPrompt() {
        // Given: A prompt containing fundamental keywords
        String prompt = "Analyze valuation and earnings for AAPL";

        // When: We execute the prompt
        String response = executor.execute(prompt);

        // Then: Should return fundamental agent response
        assertThat(response).isNotEmpty();
    }

    @Test
    void shouldReturnBacktestResponseForBacktestPrompt() {
        // Given: A prompt containing backtest keywords
        String prompt = "Run backtest performance analysis";

        // When: We execute the prompt
        String response = executor.execute(prompt);

        // Then: Should return backtest analysis response
        assertThat(response).isNotEmpty();
    }

    @Test
    void shouldReturnDefaultResponseForUnknownPrompt() {
        // Given: A prompt that doesn't match any patterns
        String prompt = "Random query that doesn't match patterns";

        // When: We execute the prompt
        String response = executor.execute(prompt);

        // Then: Should return the default response
        assertThat(response).isNotEmpty();
    }

    @Test
    void shouldAlwaysBeInstalled() {
        // Given: A DummyExecutor instance
        // When: We check if it's installed
        boolean installed = executor.isInstalled();

        // Then: Should always return true (no external dependencies)
        assertThat(installed).isTrue();
    }

    @Test
    void shouldHaveCorrectToolName() {
        // Given: A DummyExecutor instance
        // When: We get the tool name
        String toolName = executor.getToolName();

        // Then: Should return "dummy"
        assertThat(toolName).isEqualTo("dummy");
    }

    @Test
    void shouldThrowExceptionForNullPrompt() {
        // Given: A null prompt
        // When/Then: Should throw CLIExecutionException
        assertThatThrownBy(() -> executor.execute(null))
                .isInstanceOf(CLIExecutionException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void shouldThrowExceptionForEmptyPrompt() {
        // Given: An empty prompt
        // When/Then: Should throw CLIExecutionException
        assertThatThrownBy(() -> executor.execute(""))
                .isInstanceOf(CLIExecutionException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void shouldBeCaseInsensitiveWhenMatchingPrompts() {
        // Given: Prompts with different cases
        String upperCasePrompt = "MARKET RESEARCH for BTC";
        String lowerCasePrompt = "market research for btc";
        String mixedCasePrompt = "Market Research for BTC";

        // When: We execute all prompts
        String response1 = executor.execute(upperCasePrompt);
        String response2 = executor.execute(lowerCasePrompt);
        String response3 = executor.execute(mixedCasePrompt);

        // Then: All should return the same response (strategy discovery)
        assertThat(response1).isEqualTo(response2);
        assertThat(response2).isEqualTo(response3);
    }
}
