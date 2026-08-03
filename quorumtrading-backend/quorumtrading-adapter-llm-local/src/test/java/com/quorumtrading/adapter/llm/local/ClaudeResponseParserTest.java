package com.quorumtrading.adapter.llm.local;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClaudeResponseParser
 * Claude outputs JSON wrapped in markdown code blocks:
 * ```json
 * {"content": "actual LLM response"}
 * ```
 */
class ClaudeResponseParserTest {

    private ClaudeResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new ClaudeResponseParser();
    }

    @Test
    void shouldParseValidClaudeResponseWithMarkdown() throws CLIExecutionException {
        String rawOutput = """
            ```json
            {"content": "HOLD position, market uncertainty detected"}
            ```
            """;
        String result = parser.parse(rawOutput);
        assertEquals("HOLD position, market uncertainty detected", result);
    }

    @Test
    void shouldUnescapeFencedJsonInsideTheCliResultEnvelope() throws CLIExecutionException {
        // Real `claude -p ... --output-format json` envelope (captured 2026-06-10): the model's
        // answer sits ESCAPED inside the "result" string, itself wrapped in markdown fences.
        // The envelope must be JSON-parsed FIRST (so escapes resolve), THEN the fences stripped —
        // stripping fences on the raw envelope extracts still-escaped text (literal \" and \n)
        // that downstream JSON parsers reject.
        String rawOutput = "{\"type\":\"result\",\"subtype\":\"success\",\"is_error\":false,"
                + "\"result\":\"```json\\n{\\\"ticker\\\": \\\"AAPL\\\", \\\"note\\\": \\\"line1\\\\nline2\\\"}\\n```\","
                + "\"session_id\":\"abc\"}";

        String result = parser.parse(rawOutput);

        assertEquals("{\"ticker\": \"AAPL\", \"note\": \"line1\\nline2\"}", result);
    }

    @Test
    void shouldParseResponseWithoutMarkdownBlocks() throws CLIExecutionException {
        String rawOutput = "{\"content\": \"Direct JSON response\"}";
        String result = parser.parse(rawOutput);
        assertEquals("Direct JSON response", result);
    }

    @Test
    void shouldStripMultipleMarkdownBlocks() throws CLIExecutionException {
        String rawOutput = """
            Here's my analysis:
            ```json
            {"content": "BUY signal confirmed"}
            ```
            Additional text after.
            """;
        String result = parser.parse(rawOutput);
        assertEquals("BUY signal confirmed", result);
    }

    @Test
    void shouldHandleMarkdownWithLanguageSpecifier() throws CLIExecutionException {
        String rawOutput = """
            ```json
            {
              "content": "Technical indicators suggest uptrend"
            }
            ```
            """;
        String result = parser.parse(rawOutput);
        assertEquals("Technical indicators suggest uptrend", result);
    }

    @Test
    void shouldFallbackToPlainTextOnMissingContentField() throws CLIExecutionException {
        String rawOutput = """
            ```json
            {"response": "Wrong field name"}
            ```
            """;
        // With resilient parsing, this returns the JSON as plain text
        String result = parser.parse(rawOutput);
        assertTrue(result.contains("response") || result.contains("Wrong field name"));
    }

    @Test
    void shouldFallbackToPlainTextOnInvalidJson() throws CLIExecutionException {
        String rawOutput = """
            ```json
            {invalid json}
            ```
            """;
        // With resilient parsing, this returns the raw text
        String result = parser.parse(rawOutput);
        assertEquals("{invalid json}", result);
    }

    @Test
    void shouldThrowExceptionOnEmptyInput() {
        assertThrows(CLIExecutionException.class, () -> parser.parse(""));
    }

    @Test
    void shouldThrowExceptionOnNullInput() {
        assertThrows(CLIExecutionException.class, () -> parser.parse(null));
    }

    @Test
    void shouldHandleContentWithNewlines() throws CLIExecutionException {
        String rawOutput = """
            ```json
            {"content": "Action: SELL\\nReason: Overbought\\nConfidence: 0.9"}
            ```
            """;
        String result = parser.parse(rawOutput);
        assertTrue(result.contains("Action: SELL"));
        assertTrue(result.contains("Confidence: 0.9"));
    }

    @Test
    void shouldReturnCorrectToolName() {
        assertEquals("claude", parser.getToolName());
    }

    @Test
    void shouldHandleMarkdownWithoutLanguage() throws CLIExecutionException {
        String rawOutput = """
            ```
            {"content": "Response without language specifier"}
            ```
            """;
        String result = parser.parse(rawOutput);
        assertEquals("Response without language specifier", result);
    }

    @Test
    void shouldHandleNestedJsonInContent() throws CLIExecutionException {
        String rawOutput = """
            ```json
            {"content": "{\\"decision\\": \\"BUY\\", \\"amount\\": 1000}"}
            ```
            """;
        String result = parser.parse(rawOutput);
        assertTrue(result.contains("decision"));
        assertTrue(result.contains("BUY"));
    }

    @Test
    void shouldParseActualClaudeCliJsonFormat() throws CLIExecutionException {
        // Actual format from: claude --output-format json --model haiku --dangerously-skip-permissions -p "say hello"
        String rawOutput = """
            {"type":"result","subtype":"success","is_error":false,"duration_ms":3517,"duration_api_ms":8757,"num_turns":1,"result":"Hello! 👋 \\n\\nI'm Claude Code, ready to help you with your QuorumTrading project. I can assist with:\\n\\n- **Backend Development** (Quarkus/Java) - agents, orchestration, APIs, database\\n- **Frontend Development** (Next.js/React/TypeScript) - components, API integration, WebSocket\\n- **Testing** - unit tests, integration tests, E2E tests\\n- **Database** - schema migrations, queries, debugging\\n- **Architecture** - design patterns, code structure, refactoring\\n- **DevOps** - configuration, Docker, deployment\\n\\nWhat would you like to work on today?","session_id":"82151634-6c2b-4fa5-9450-c2e91bda5f51","total_cost_usd":0.00216,"usage":{"input_tokens":3,"cache_creation_input_tokens":47300,"cache_read_input_tokens":0,"output_tokens":146,"server_tool_use":{"web_search_requests":0},"service_tier":"standard","cache_creation":{"ephemeral_1h_input_tokens":0,"ephemeral_5m_input_tokens":47300}},"modelUsage":{"claude-haiku-4-5-20251001":{"inputTokens":0,"outputTokens":432,"cacheReadInputTokens":0,"cacheCreationInputTokens":0,"webSearchRequests":0,"costUSD":0.00216,"contextWindow":200000}},"permission_denials":[],"uuid":"6d0455e1-5c31-4340-b9cc-2a18cb6937f9"}
            """;
        String result = parser.parse(rawOutput);

        // Should extract the "result" field content
        assertTrue(result.contains("Hello!"));
        assertTrue(result.contains("Claude Code"));
        assertTrue(result.contains("QuorumTrading project"));
    }

    @Test
    void shouldParseClaudeCliJsonFormatWithBuyDecision() throws CLIExecutionException {
        // Simulated actual format with trading decision
        String rawOutput = """
            {"type":"result","subtype":"success","is_error":false,"num_turns":1,"result":"BUY 100 shares of AAPL at current market price","session_id":"test-123"}
            """;
        String result = parser.parse(rawOutput);
        assertEquals("BUY 100 shares of AAPL at current market price", result);
    }
}
