package com.quorumtrading.adapter.mcp.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolBaseParseDateTest {

    private final TestableToolBase tool = new TestableToolBase();

    @ParameterizedTest(name = "parseDate(\"{0}\") = {1}")
    @CsvSource({
        // Plain date
        "2026-01-01,           2026-01-01T00:00:00Z",
        "2026-02-26,           2026-02-26T00:00:00Z",

        // Full ISO-8601 with seconds
        "2026-01-01T00:00:00Z, 2026-01-01T00:00:00Z",
        "2026-02-26T14:30:00Z, 2026-02-26T14:30:00Z",

        // Missing seconds (common LLM format)
        "2026-02-26T00:00Z,    2026-02-26T00:00:00Z",
        "2026-01-01T12:30Z,    2026-01-01T12:30:00Z",

        // With milliseconds
        "2026-02-26T00:00:00.000Z, 2026-02-26T00:00:00Z",

        // With offset instead of Z
        "2026-02-26T00:00:00+00:00, 2026-02-26T00:00:00Z",
        "2026-02-26T01:00:00+01:00, 2026-02-26T00:00:00Z",
    })
    void shouldParseLenientDateFormats(String input, String expected) {
        Instant result = tool.parseDate(input);
        assertThat(result).isEqualTo(Instant.parse(expected));
    }

    /**
     * Minimal concrete subclass to test the protected parseDate method.
     */
    private static class TestableToolBase extends McpToolBase {
        @Override
        public Instant parseDate(String dateStr) {
            return super.parseDate(dateStr);
        }
    }
}
