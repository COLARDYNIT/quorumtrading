package com.quorumtrading.adapter.llm.local;

/**
 * Exception thrown when CLI tool execution fails.
 * <p>
 * This exception is used to wrap various failure scenarios that can occur
 * when executing local CLI-based LLM tools, including:
 * <ul>
 *   <li>Tool not found or not installed</li>
 *   <li>Process execution errors</li>
 *   <li>Authentication/authorization failures</li>
 *   <li>Timeout or resource exhaustion</li>
 *   <li>Invalid command output or parsing errors</li>
 * </ul>
 * </p>
 *
 * @author QuorumTrading
 * @since 1.0.0
 */
public class CLIExecutionException extends RuntimeException {

    /**
     * Constructs a new CLI execution exception with the specified detail message.
     *
     * @param message The detail message explaining the failure
     */
    public CLIExecutionException(String message) {
        super(message);
    }

    /**
     * Constructs a new CLI execution exception with the specified detail message
     * and cause.
     *
     * @param message The detail message explaining the failure
     * @param cause   The underlying cause of the failure (e.g., IOException, InterruptedException)
     */
    public CLIExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
