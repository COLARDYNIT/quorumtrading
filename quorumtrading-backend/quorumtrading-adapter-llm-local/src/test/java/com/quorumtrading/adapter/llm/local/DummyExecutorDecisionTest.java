package com.quorumtrading.adapter.llm.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DummyExecutor's deterministic agent-decision mode.
 * <p>
 * Agent-decision prompts (Technical/Fundamental/Sentiment/Orchestrator) always end with
 * {@code AgentResponseFormat.JSON_RESPONSE_INSTRUCTION}, which contains the marker
 * "Respond in JSON format ONLY". For those prompts the DummyExecutor must return a
 * parseable JSON decision instead of canned markdown:
 * <ul>
 *   <li>flat portfolio ("(no position)") → BUY with high confidence</li>
 *   <li>existing position ("N units (avg cost ...)") → HOLD</li>
 * </ul>
 * This makes the core-loop e2e deterministic: one BUY on the first evaluation, HOLD after.
 */
class DummyExecutorDecisionTest {

    private static final String JSON_INSTRUCTION = """

            Respond in JSON format ONLY (no additional text, code, or suggestions):
            {"action":"BUY","symbol":"BTC","confidence":0.75,"reasoning":"Your analysis reasoning here"}

            The "quantity" field is OPTIONAL. If you omit it or set it to 0, the system will automatically \
            use the maximum quantity allowed by the strategy.
            """;

    private DummyExecutor executor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        executor = new DummyExecutor();
    }

    @Test
    void shouldReturnBuyJsonWhenNoPositionHeld() throws Exception {
        String prompt = """
                You are a technical analysis agent. Analyze RSI and MACD for BTC.
                Portfolio State:
                - Cash Available: $10000.00
                - Current Holdings of BTC: 0 units (no position)
                """ + JSON_INSTRUCTION;

        String response = executor.execute(prompt);

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("action").asText()).isEqualTo("BUY");
        assertThat(json.get("symbol").asText()).isEqualTo("BTC");
        assertThat(json.get("confidence").asDouble()).isBetween(0.6, 1.0);
        assertThat(json.get("reasoning").asText()).isNotEmpty();
    }

    @Test
    void shouldReturnHoldJsonWhenPositionHeld() throws Exception {
        String prompt = """
                You are a sentiment analysis agent. Analyze news sentiment for ETH.
                Portfolio State:
                - Cash Available: $12.50
                - Current Holdings of ETH: 3.25 units (avg cost: $3100.00/unit)
                """ + JSON_INSTRUCTION;

        String response = executor.execute(prompt);

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("action").asText()).isEqualTo("HOLD");
        assertThat(json.get("symbol").asText()).isEqualTo("ETH");
        assertThat(json.get("confidence").asDouble()).isBetween(0.6, 1.0);
        assertThat(json.get("reasoning").asText()).isNotEmpty();
    }

    @Test
    void shouldFallBackToBtcSymbolWhenHoldingsLineAbsent() throws Exception {
        String prompt = "Some agent prompt without a holdings line." + JSON_INSTRUCTION;

        String response = executor.execute(prompt);

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("action").asText()).isEqualTo("BUY");
        assertThat(json.get("symbol").asText()).isEqualTo("BTC");
    }

    @Test
    void shouldStillReturnMarkdownForResearchPrompts() {
        // No JSON instruction marker → existing keyword-based canned markdown must be untouched
        String prompt = "Analyze market research for BTC momentum strategy";

        String response = executor.execute(prompt);

        assertThat(response).contains("# ");
    }

    @Test
    void decisionModeWinsOverKeywordTable() throws Exception {
        // Prompt contains "backtest" (keyword table would pick backtest-analysis.md), but the
        // JSON instruction marker means this is an agent decision — JSON must win.
        String prompt = """
                Strategy: buy the dip. This strategy was designed after a backtest of RSI signals.
                - Current Holdings of SOL: 0 units (no position)
                """ + JSON_INSTRUCTION;

        String response = executor.execute(prompt);

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("action").asText()).isEqualTo("BUY");
        assertThat(json.get("symbol").asText()).isEqualTo("SOL");
    }
}
