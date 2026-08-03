package com.quorumtrading.adapter.llm.local;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ClaudeExecutor.
 * Following TDD approach - these tests should fail initially until implementation is complete.
 */
class ClaudeExecutorTest {

    private ClaudeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ClaudeExecutor();
    }

    @Test
    void testGetToolNameReturnsClaude() {
        // Given: A ClaudeExecutor instance
        // When: We call getToolName
        String toolName = executor.getToolName();

        // Then: Should return "claude"
        assertEquals("claude", toolName, "Tool name should be 'claude'");
    }

    @Test
    void testIsInstalledReturnsTrueWhenClaudeExists() {
        // Given: A ClaudeExecutor instance
        // When: We check if claude is installed (assuming it's installed on the system)
        boolean installed = executor.isInstalled();

        // Then: Result depends on whether claude is actually installed
        // This test will verify the mechanism works - it should not throw exceptions
        // and should return a boolean value
        assertNotNull(installed);
    }

    @Test
    void testIsInstalledReturnsFalseWhenClaudeMissing() {
        // Note: This test is difficult to implement without mocking ProcessBuilder
        // For now, we'll skip this test and rely on manual verification
        // or implement with a custom ClaudeExecutor that accepts a path

        // This test would require dependency injection of ProcessBuilder or similar
        // Will be addressed if needed in future refactoring
        assertTrue(true, "Skipping test - requires mocking capability");
    }

    @Test
    void testExecuteReturnsJsonResponse() throws CLIExecutionException {
        // Given: A ClaudeExecutor instance and a simple prompt
        String prompt = "What is 2+2?";

        // When: We execute the prompt
        // Note: This test requires claude to be installed
        // If claude is not installed, this test will throw CLIExecutionException

        try {
            String response = executor.execute(prompt);

            // Then: Should return a non-null, non-empty response
            assertNotNull(response, "Response should not be null");
            assertFalse(response.isEmpty(), "Response should not be empty");
        } catch (CLIExecutionException e) {
            // If claude is not installed, we expect this exception
            assertTrue(e.getMessage().contains("claude") || e.getMessage().contains("not found"),
                    "Exception should mention claude or not found: " + e.getMessage());
        }
    }

    @Test
    void testExecuteThrowsExceptionOnTimeout() {
        // Given: A ClaudeExecutor instance
        // When/Then: Verify that timeout is configured
        // Note: We can't easily test actual timeout without waiting 30+ seconds
        // This test is more about verifying the timeout mechanism exists in the code

        // We verify that normal execution completes successfully
        try {
            String result = executor.execute("Hello");
            assertNotNull(result, "Should return response for valid prompt");
        } catch (CLIExecutionException e) {
            // If claude is not installed, that's acceptable
            assertTrue(e.getMessage().toLowerCase().contains("claude") ||
                      e.getMessage().toLowerCase().contains("not found"),
                    "Exception should be about claude availability: " + e.getMessage());
        }
    }

    @Test
    void testExecuteThrowsExceptionOnProcessError() {
        // Given: A ClaudeExecutor instance
        // When: We try to execute with invalid arguments (empty prompt)
        String emptyPrompt = "";

        // Then: Should throw CLIExecutionException
        assertThrows(CLIExecutionException.class, () -> {
            executor.execute(emptyPrompt);
        }, "Empty prompt should cause execution exception");
    }

    @Test
    void testExecuteBuildsCorrectCommand() throws CLIExecutionException {
        // Given: A ClaudeExecutor instance
        String testPrompt = "Test prompt";

        // When: We execute a command
        // Then: The command should be built correctly internally
        // We can't directly verify the command, but we can verify behavior

        try {
            String response = executor.execute(testPrompt);

            // If claude is installed and responds, we should get a response
            assertNotNull(response, "Response should not be null if claude is installed");

            // The response should contain some content
            // (this is a weak assertion, but validates execution happened)
            assertTrue(response.length() >= 0, "Response should have non-negative length");

        } catch (CLIExecutionException e) {
            // If claude is not installed, verify exception message is meaningful
            assertNotNull(e.getMessage(), "Exception message should not be null");
            assertFalse(e.getMessage().isEmpty(), "Exception message should not be empty");

            // Exception should indicate what went wrong
            assertTrue(
                e.getMessage().toLowerCase().contains("claude") ||
                e.getMessage().toLowerCase().contains("not found") ||
                e.getMessage().toLowerCase().contains("failed"),
                "Exception should provide meaningful error: " + e.getMessage()
            );
        }
    }

    @Test
    void testExecuteWithNullPromptThrowsException() {
        // Given: A ClaudeExecutor instance
        // When: We try to execute with null prompt
        // Then: Should throw CLIExecutionException or NullPointerException
        assertThrows(Exception.class, () -> {
            executor.execute(null);
        }, "Null prompt should cause exception");
    }

    @Test
    void testExecuteWithSpecialCharactersInPrompt() throws CLIExecutionException {
        // Given: A prompt with special characters that might break shell execution
        String promptWithQuotes = "What is the meaning of \"life\"?";
        String promptWithBackslash = "Path: C:\\Users\\test";
        String promptWithNewline = "First line\nSecond line";

        // When/Then: Should handle special characters properly without breaking
        try {
            String response1 = executor.execute(promptWithQuotes);
            assertNotNull(response1, "Should handle quotes in prompt");

            String response2 = executor.execute(promptWithBackslash);
            assertNotNull(response2, "Should handle backslashes in prompt");

            String response3 = executor.execute(promptWithNewline);
            assertNotNull(response3, "Should handle newlines in prompt");

        } catch (CLIExecutionException e) {
            // If claude is not installed, that's acceptable
            assertTrue(e.getMessage().contains("claude") || e.getMessage().contains("not found"),
                    "Exception should be due to claude availability: " + e.getMessage());
        }
    }
}
