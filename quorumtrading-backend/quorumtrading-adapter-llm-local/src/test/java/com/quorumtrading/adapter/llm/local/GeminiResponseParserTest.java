package com.quorumtrading.adapter.llm.local;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GeminiResponseParser
 * Gemini outputs nested JSON structure:
 * {"response": {"text": "actual LLM response"}, "stats": {...}}
 */
class GeminiResponseParserTest {

    private GeminiResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new GeminiResponseParser();
    }

    @Test
    void shouldParseValidGeminiResponse() throws CLIExecutionException {
        String rawOutput = """
            {
              "response": {
                "text": "Strong buy signal based on momentum indicators"
              },
              "stats": {
                "tokens": 100,
                "latency_ms": 250
              }
            }
            """;
        String result = parser.parse(rawOutput);
        assertEquals("Strong buy signal based on momentum indicators", result);
    }

    @Test
    void shouldHandleMinimalGeminiResponse() throws CLIExecutionException {
        String rawOutput = "{\"response\": {\"text\": \"HOLD\"}}";
        String result = parser.parse(rawOutput);
        assertEquals("HOLD", result);
    }

    @Test
    void shouldHandleComplexTextContent() throws CLIExecutionException {
        String rawOutput = """
            {
              "response": {
                "text": "Decision: BUY\\nAsset: BTC\\nAmount: 0.5\\nReason: Breakout detected"
              }
            }
            """;
        String result = parser.parse(rawOutput);
        assertTrue(result.contains("Decision: BUY"));
        assertTrue(result.contains("Reason: Breakout detected"));
    }

    @Test
    void shouldFallbackToPlainTextOnMissingResponseField() throws CLIExecutionException {
        String rawOutput = """
            {
              "data": {
                "text": "Wrong structure"
              }
            }
            """;
        // With resilient parsing, this returns the JSON as plain text
        String result = parser.parse(rawOutput);
        assertTrue(result.contains("data") || result.contains("Wrong structure"));
    }

    @Test
    void shouldFallbackToPlainTextOnMissingTextField() throws CLIExecutionException {
        String rawOutput = """
            {
              "response": {
                "content": "Wrong field name"
              }
            }
            """;
        // With resilient parsing, this returns the JSON as plain text
        String result = parser.parse(rawOutput);
        assertTrue(result.contains("content") || result.contains("Wrong field name"));
    }

    @Test
    void shouldFallbackToPlainTextOnNullResponseObject() throws CLIExecutionException {
        String rawOutput = "{\"response\": null}";
        // With resilient parsing, this returns the raw text
        String result = parser.parse(rawOutput);
        assertTrue(result.contains("response") || result.contains("null"));
    }

    @Test
    void shouldFallbackToPlainTextOnInvalidJson() throws CLIExecutionException {
        String rawOutput = "{invalid: json}";
        // With resilient parsing, this returns the raw text
        String result = parser.parse(rawOutput);
        assertEquals("{invalid: json}", result);
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
    void shouldReturnCorrectToolName() {
        assertEquals("gemini", parser.getToolName());
    }

    @Test
    void shouldHandleTextWithEscapedCharacters() throws CLIExecutionException {
        String rawOutput = """
            {
              "response": {
                "text": "Signal: \\"BUY\\" - Confidence: 85%"
              }
            }
            """;
        String result = parser.parse(rawOutput);
        assertTrue(result.contains("\"BUY\""));
    }

    @Test
    void shouldHandleEmptyStatsObject() throws CLIExecutionException {
        String rawOutput = """
            {
              "response": {
                "text": "Market analysis complete"
              },
              "stats": {}
            }
            """;
        String result = parser.parse(rawOutput);
        assertEquals("Market analysis complete", result);
    }

    @Test
    void shouldHandleAdditionalFieldsInResponse() throws CLIExecutionException {
        String rawOutput = """
            {
              "response": {
                "text": "BUY recommendation",
                "confidence": 0.95,
                "metadata": {}
              }
            }
            """;
        String result = parser.parse(rawOutput);
        assertEquals("BUY recommendation", result);
    }

    @Test
    void shouldParseActualGeminiCliJsonFormat() throws CLIExecutionException {
        // Actual format from: gemini --output-format json --yolo "say hello"
        // The response field is a direct string, not an object with text field
        String rawOutput = """
            {
              "response": "Hello! I'm ready to help you with your code.",
              "stats": {
                "models": {
                  "flash": {
                    "api": {
                      "totalRequests": 1,
                      "totalErrors": 0,
                      "totalLatencyMs": 250
                    },
                    "tokens": {
                      "prompt": 10,
                      "candidates": 15,
                      "total": 25
                    }
                  }
                }
              }
            }
            """;
        String result = parser.parse(rawOutput);
        assertEquals("Hello! I'm ready to help you with your code.", result);
    }

    @Test
    void shouldParseGeminiCliJsonFormatWithTradingDecision() throws CLIExecutionException {
        // Simulated actual format with trading decision
        String rawOutput = """
            {
              "response": "SELL 50 shares of GOOGL - overbought RSI detected",
              "stats": {
                "models": {
                  "flash": {
                    "tokens": {
                      "total": 30
                    }
                  }
                }
              }
            }
            """;
        String result = parser.parse(rawOutput);
        assertEquals("SELL 50 shares of GOOGL - overbought RSI detected", result);
    }
}
