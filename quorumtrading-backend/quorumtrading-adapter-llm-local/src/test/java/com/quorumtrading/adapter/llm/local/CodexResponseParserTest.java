package com.quorumtrading.adapter.llm.local;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CodexResponseParser
 * Codex outputs JSON on stdout: {"response": "actual LLM response", "metadata": {...}}
 */
class CodexResponseParserTest {

    private CodexResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new CodexResponseParser();
    }

    @Test
    void shouldParseValidCodexResponse() throws CLIExecutionException {
        String rawOutput = "{\"response\": \"Buy BTC based on technical analysis\", \"metadata\": {\"tokens\": 50}}";
        String result = parser.parse(rawOutput);
        assertEquals("Buy BTC based on technical analysis", result);
    }

    @Test
    void shouldHandleResponseWithComplexContent() throws CLIExecutionException {
        String rawOutput = "{\"response\": \"Action: BUY\\nReason: Strong uptrend\\nConfidence: 0.8\", \"metadata\": {}}";
        String result = parser.parse(rawOutput);
        assertTrue(result.contains("Action: BUY"));
        assertTrue(result.contains("Confidence: 0.8"));
    }

    @Test
    void shouldFallbackToPlainTextOnMissingResponseField() throws CLIExecutionException {
        String rawOutput = "{\"data\": \"some data\", \"metadata\": {}}";
        // With resilient parsing, this returns the JSON as plain text
        String result = parser.parse(rawOutput);
        assertTrue(result.contains("data") || result.contains("some data"));
    }

    @Test
    void shouldFallbackToPlainTextOnInvalidJson() throws CLIExecutionException {
        String rawOutput = "{invalid json}";
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
    void shouldHandleResponseWithEscapedCharacters() throws CLIExecutionException {
        String rawOutput = "{\"response\": \"Action: \\\"BUY\\\" with 50% allocation\", \"metadata\": {}}";
        String result = parser.parse(rawOutput);
        assertTrue(result.contains("\"BUY\""));
    }

    @Test
    void shouldReturnCorrectToolName() {
        assertEquals("codex", parser.getToolName());
    }

    @Test
    void shouldHandleWhitespaceInJson() throws CLIExecutionException {
        String rawOutput = """
            {
              "response": "Market conditions favorable",
              "metadata": {
                "model": "gpt-4"
              }
            }
            """;
        String result = parser.parse(rawOutput);
        assertEquals("Market conditions favorable", result);
    }

    @Test
    void shouldExtractResponseFromCodexTextFormat() throws CLIExecutionException {
        String rawOutput = """
            OpenAI Codex v0.53.0 (research preview)
            --------
            workdir: /Users/test/project/quorumtrading-backend
            model: gpt-5
            provider: openai
            approval: never
            sandbox: workspace-write [workdir, /tmp, $TMPDIR]
            reasoning effort: low
            reasoning summaries: auto
            session id: 019a4e02-7690-7c80-bbac-a59053adf169
            --------
            user
            say hello
            codex
            Hello! How can I help you today?
            tokens used
            11,400
            Hello! How can I help you today?
            """;

        String result = parser.parse(rawOutput);
        assertEquals("Hello! How can I help you today?", result);
    }

    @Test
    void shouldExtractJsonResponseFromCodexTextFormat() throws CLIExecutionException {
        String rawOutput = """
            OpenAI Codex v0.53.0 (research preview)
            --------
            workdir: /Users/test/project
            model: gpt-5
            --------
            user
            Analyze BTC
            codex
            {"action":"BUY","symbol":"BTC","quantity":0.1,"confidence":0.85,"reasoning":"Strong uptrend detected"}
            tokens used
            8,500
            {"action":"BUY","symbol":"BTC","quantity":0.1,"confidence":0.85,"reasoning":"Strong uptrend detected"}
            """;

        String result = parser.parse(rawOutput);
        assertTrue(result.contains("\"action\":\"BUY\""));
        assertTrue(result.contains("\"symbol\":\"BTC\""));
        assertTrue(result.contains("\"confidence\":0.85"));
    }

    @Test
    void shouldExtractMultilineResponseFromCodexTextFormat() throws CLIExecutionException {
        String rawOutput = """
            OpenAI Codex v0.53.0
            --------
            model: gpt-5
            --------
            user
            analyze market
            codex
            Based on technical analysis:
            - Price: $50,000
            - Trend: Upward
            - Recommendation: BUY
            tokens used
            5,200
            Based on technical analysis:
            - Price: $50,000
            - Trend: Upward
            - Recommendation: BUY
            """;

        String result = parser.parse(rawOutput);
        assertTrue(result.contains("Based on technical analysis:"));
        assertTrue(result.contains("Price: $50,000"));
        assertTrue(result.contains("Recommendation: BUY"));
    }

    @Test
    void shouldHandleCodexOutputWithNoUserSection() throws CLIExecutionException {
        String rawOutput = """
            OpenAI Codex v0.53.0
            --------
            codex
            Response without user section
            tokens used
            100
            """;

        String result = parser.parse(rawOutput);
        assertEquals("Response without user section", result);
    }
}
