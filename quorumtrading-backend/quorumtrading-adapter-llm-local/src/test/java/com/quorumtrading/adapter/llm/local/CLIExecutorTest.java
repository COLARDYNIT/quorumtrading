package com.quorumtrading.adapter.llm.local;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for CLIExecutor interface and CLIExecutionException.
 * Following TDD approach - these tests should fail initially.
 */
class CLIExecutorTest {

    @Test
    void testInterfaceCanBeMocked() {
        // Given: A mocked CLIExecutor
        CLIExecutor executor = Mockito.mock(CLIExecutor.class);

        // When: We configure mock behavior
        when(executor.getToolName()).thenReturn("test-tool");
        when(executor.isInstalled()).thenReturn(true);

        // Then: Mock should work as expected
        assertEquals("test-tool", executor.getToolName());
        assertTrue(executor.isInstalled());
        verify(executor).getToolName();
        verify(executor).isInstalled();
    }

    @Test
    void testExecuteMethodCanBeStubbed() throws CLIExecutionException {
        // Given: A mocked CLIExecutor with stubbed execute method
        CLIExecutor executor = Mockito.mock(CLIExecutor.class);
        String testPrompt = "Test prompt";
        String expectedResponse = "Test response";

        when(executor.execute(testPrompt)).thenReturn(expectedResponse);

        // When: We call execute
        String result = executor.execute(testPrompt);

        // Then: Should return stubbed response
        assertEquals(expectedResponse, result);
        verify(executor).execute(testPrompt);
    }

    @Test
    void testExecuteMethodCanThrowCLIExecutionException() {
        // Given: A mocked CLIExecutor configured to throw exception
        CLIExecutor executor = Mockito.mock(CLIExecutor.class);
        String testPrompt = "Test prompt";
        String errorMessage = "Execution failed";

        when(executor.execute(testPrompt))
            .thenThrow(new CLIExecutionException(errorMessage));

        // When/Then: Should throw CLIExecutionException
        CLIExecutionException exception = assertThrows(
            CLIExecutionException.class,
            () -> executor.execute(testPrompt)
        );

        assertEquals(errorMessage, exception.getMessage());
    }

    @Test
    void testCLIExecutionExceptionWithMessage() {
        // Given: An error message
        String errorMessage = "Tool execution failed";

        // When: Creating exception with message
        CLIExecutionException exception = new CLIExecutionException(errorMessage);

        // Then: Exception should contain the message
        assertEquals(errorMessage, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testCLIExecutionExceptionWithMessageAndCause() {
        // Given: An error message and a cause
        String errorMessage = "Tool execution failed";
        RuntimeException cause = new RuntimeException("Underlying error");

        // When: Creating exception with message and cause
        CLIExecutionException exception = new CLIExecutionException(errorMessage, cause);

        // Then: Exception should contain both message and cause
        assertEquals(errorMessage, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals("Underlying error", exception.getCause().getMessage());
    }

    @Test
    void testExceptionCauseChaining() {
        // Given: A chain of exceptions
        Exception rootCause = new Exception("Root cause");
        RuntimeException intermediateCause = new RuntimeException("Intermediate", rootCause);
        CLIExecutionException topException = new CLIExecutionException("Top level", intermediateCause);

        // Then: Exception chain should be preserved
        assertEquals("Top level", topException.getMessage());
        assertEquals(intermediateCause, topException.getCause());
        assertEquals(rootCause, topException.getCause().getCause());
    }

    @Test
    void testIsInstalledReturnsFalseForMissingTool() {
        // Given: A mocked CLIExecutor for a missing tool
        CLIExecutor executor = Mockito.mock(CLIExecutor.class);

        when(executor.isInstalled()).thenReturn(false);

        // When/Then: Should return false for missing tool
        assertFalse(executor.isInstalled());
    }

    @Test
    void testGetToolNameReturnsIdentifier() {
        // Given: A mocked CLIExecutor
        CLIExecutor executor = Mockito.mock(CLIExecutor.class);

        when(executor.getToolName()).thenReturn("claude");

        // When/Then: Should return tool identifier
        assertEquals("claude", executor.getToolName());
    }
}
