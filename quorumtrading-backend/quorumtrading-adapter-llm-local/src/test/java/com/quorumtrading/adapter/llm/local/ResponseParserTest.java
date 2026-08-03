package com.quorumtrading.adapter.llm.local;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Interface contract tests for ResponseParser
 */
class ResponseParserTest {

    @Test
    void shouldReturnToolName() {
        ResponseParser parser = new TestResponseParser();
        assertEquals("test-tool", parser.getToolName());
    }

    @Test
    void shouldParseValidInput() throws CLIExecutionException {
        ResponseParser parser = new TestResponseParser();
        String result = parser.parse("valid input");
        assertNotNull(result);
    }

    @Test
    void shouldThrowExceptionOnInvalidInput() {
        ResponseParser parser = new TestResponseParser();
        assertThrows(CLIExecutionException.class, () -> parser.parse("invalid"));
    }

    // Test implementation for interface contract verification
    private static class TestResponseParser implements ResponseParser {
        @Override
        public String parse(String rawOutput) throws CLIExecutionException {
            if ("invalid".equals(rawOutput)) {
                throw new CLIExecutionException("Invalid input for test-tool");
            }
            return "parsed: " + rawOutput;
        }

        @Override
        public String getToolName() {
            return "test-tool";
        }
    }
}
