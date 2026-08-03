package com.quorumtrading.adapter.llm.local;

/**
 * Parses and normalizes raw CLI output from different LLM tools.
 * Each LLM tool (Codex, Claude, Gemini) outputs responses in different formats,
 * and this interface provides a contract for normalizing them into plain text.
 */
public interface ResponseParser {

    /**
     * Parses raw CLI output and extracts the actual LLM response.
     *
     * @param rawOutput Raw output from CLI tool (may contain JSON, markdown, metadata, etc.)
     * @return Normalized LLM response text
     * @throws CLIExecutionException if parsing fails due to invalid format or missing fields
     */
    String parse(String rawOutput) throws CLIExecutionException;

    /**
     * Token counts the tool reported, when it reported any.
     *
     * <p>Empty is the honest default and means <b>unknown</b>, not "no tokens were used" — most of
     * these CLIs never report usage at all. A parser must only return a value when the tool's own
     * output carried the numbers; estimating them here would put invented figures in front of a
     * user as measurements.</p>
     *
     * @param rawOutput raw output from the CLI tool
     * @return the reported usage, or empty when the tool said nothing about it
     */
    default java.util.Optional<com.quorumtrading.model.valueobject.LLMUsage> parseUsage(String rawOutput) {
        return java.util.Optional.empty();
    }

    /**
     * Returns the name of the tool this parser handles.
     *
     * @return Tool name (e.g., "codex", "claude", "gemini")
     */
    String getToolName();
}
