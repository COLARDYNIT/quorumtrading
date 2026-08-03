package com.quorumtrading.adapter.llm.local;

/**
 * Interface for executing local CLI-based LLM tools.
 * <p>
 * This abstraction allows different CLI tools (Claude Desktop, Codex, Gemini, etc.)
 * to be used interchangeably for local LLM inference.
 * </p>
 *
 * @author QuorumTrading
 * @since 1.0.0
 */
public interface CLIExecutor {

    /**
     * Executes the CLI tool with the given prompt and returns the response.
     *
     * @param prompt The prompt to send to the LLM
     * @return The raw response from the CLI tool
     * @throws CLIExecutionException if execution fails for any reason
     */
    String execute(String prompt) throws CLIExecutionException;

    /**
     * Executes the CLI tool for a specific model, optionally at a given reasoning effort.
     *
     * <p>The model used to be a string literal in each executor — {@code --model haiku},
     * {@code --model gpt-5} — so the six-tier resolution chain computed a model that reached
     * nothing but log lines. Passing it here is what makes the chain mean something on this engine.</p>
     *
     * <p>Default implementation ignores both, which is the honest behaviour for a tool whose flag
     * surface has not been verified: a wrong flag makes every call fail at run time, so an
     * unverified capability is modelled as absent.</p>
     *
     * @param prompt the prompt to send
     * @param model  the model to run, or null when none was resolved
     * @param effort reasoning effort, or null when none was requested — an engine that supports
     *               effort must OMIT the flag rather than default it
     */
    default String execute(String prompt, String model,
                           com.quorumtrading.model.valueobject.ModelEffort effort) throws CLIExecutionException {
        return execute(prompt);
    }

    /**
     * The models this tool can be asked to run, empty when its flag surface is unverified.
     *
     * <p>Only tools actually probed on a real machine appear here. A guessed model list is worse
     * than none: it would let a user pin something the tool rejects, which fails at run time in the
     * middle of a backtest.</p>
     */
    default java.util.List<String> supportedModels() {
        return java.util.List.of();
    }

    /** Whether this tool accepts a reasoning-effort flag. Verified per tool, never assumed. */
    default boolean supportsEffort() {
        return false;
    }

    /**
     * Returns the name of the CLI tool being executed.
     * <p>
     * Examples: "codex", "claude", "gemini"
     * </p>
     *
     * @return The identifier/name of the CLI tool
     */
    String getToolName();

    /**
     * Checks if the CLI tool is installed and accessible on the system.
     * <p>
     * Implementations should verify that:
     * <ul>
     *   <li>The CLI executable exists in the system PATH or configured location</li>
     *   <li>The tool is properly configured (e.g., API keys, auth tokens)</li>
     *   <li>The tool can be executed by the current user</li>
     * </ul>
     * </p>
     *
     * @return true if the tool is available and ready to use, false otherwise
     */
    boolean isInstalled();
}
