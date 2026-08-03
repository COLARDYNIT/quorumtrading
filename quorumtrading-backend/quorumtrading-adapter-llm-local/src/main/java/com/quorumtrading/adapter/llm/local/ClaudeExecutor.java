package com.quorumtrading.adapter.llm.local;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Executor for the Claude CLI tool.
 * <p>
 * Executes the 'claude' command-line tool with the following format:
 * <code>claude -p "&lt;prompt&gt;" --output-format json --dangerously-skip-permissions</code>
 * </p>
 * <p>
 * Claude outputs JSON on stdout which may contain markdown code blocks.
 * Response parsing will be handled separately by a response parser.
 * </p>
 *
 * @author QuorumTrading
 * @since 1.0.0
 */
@ApplicationScoped
public class ClaudeExecutor implements CLIExecutor {

    private static final Logger LOG = Logger.getLogger(ClaudeExecutor.class);
    private static final String TOOL_NAME = "claude";
    private static final long TIMEOUT_SECONDS = 120;

    /**
     * Aliases {@code claude --model} accepts, verified from {@code claude --help}.
     *
     * <p>Aliases rather than dated full names ({@code claude-fable-5}) because an alias always
     * points at the current model of that class, and a pinned dated name silently rots.</p>
     */
    private static final java.util.List<String> SUPPORTED_MODELS =
            java.util.List.of("haiku", "sonnet", "opus", "fable");

    /** The model used when resolution produced nothing — the historical hardcoded value. */
    private static final String DEFAULT_MODEL = "haiku";

    @Override
    public java.util.List<String> supportedModels() {
        return SUPPORTED_MODELS;
    }

    /** Verified present: {@code --effort <low|medium|high|xhigh|max>}. */
    @Override
    public boolean supportsEffort() {
        return true;
    }

    @Override
    public String execute(String prompt) throws CLIExecutionException {
        return execute(prompt, null, null);
    }

    @Override
    public String execute(String prompt, String model, com.quorumtrading.model.valueobject.ModelEffort effort)
            throws CLIExecutionException {
        if (prompt == null) {
            throw new CLIExecutionException("Prompt cannot be null");
        }

        if (prompt.isEmpty()) {
            throw new CLIExecutionException("Prompt cannot be empty");
        }

        String requestedModel = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();

        LOG.debugf("Executing claude with model %s (prompt length: %d characters)",
                requestedModel, prompt.length());

        return runProcess(new ProcessBuilder(buildCommand(prompt, requestedModel, effort)));
    }

    /**
     * The exact argv handed to {@code claude}.
     *
     * <p>Extracted so the flag logic can be asserted without starting a process — the model used to
     * be the string literal {@code haiku} here, which is why the six-tier chain reached nothing but
     * log lines on this engine.</p>
     *
     * <p>{@code --fallback-model} is deliberately NOT passed. It is a cross-MODEL fallback, while
     * this adapter already runs a cross-TOOL chain; enabling both means two independent layers of
     * silent substitution, which is the defect this whole change removes.</p>
     */
    static java.util.List<String> buildCommand(String prompt, String model, com.quorumtrading.model.valueobject.ModelEffort effort) {
        java.util.List<String> command = new java.util.ArrayList<>(java.util.List.of(
            TOOL_NAME, "-p", prompt,
            "--strict-mcp-config", "--output-format", "json",
            "--model", model));
        // No effort requested means NO flag: inventing a level for a caller who asked for none is
        // the same class of guess this work exists to remove.
        if (effort != null) {
            command.add("--effort");
            command.add(effort.flagValue());
        }
        return command;
    }

    /**
     * Run the built command and return stdout, or fail with whatever the tool said.
     *
     * <p>Extracted from {@code execute} purely so building the command and running it are separate
     * concerns — adding the model and effort flags pushed the single method past the complexity the
     * build enforces.</p>
     */
    String runProcess(ProcessBuilder processBuilder) throws CLIExecutionException {
        processBuilder.redirectErrorStream(false);
        LOG.debug("Starting claude process...");
        // Pipe handling — closing the child's stdin above all — lives in CliProcessRunner.
        // The prompt travels as an argv element, so there is nothing to write to stdin; leaving it
        // open is what hangs a CLI that reads stdin when it is not a TTY.
        return CliProcessRunner.run(processBuilder, TIMEOUT_SECONDS, "Claude");
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public boolean isInstalled() {
        try {
            // Try to execute: claude --version
            ProcessBuilder processBuilder = new ProcessBuilder(TOOL_NAME, "--version");
            processBuilder.redirectErrorStream(true);

            LOG.debug("Checking if claude is installed...");
            Process process = processBuilder.start();
            // A version probe reads no stdin either, but it is started the same way and would hang
            // the same way. isInstalled() runs on every fallback-chain attempt.
            CliProcessRunner.closeStdin(process, "Claude");

            // Wait for process with a short timeout (5 seconds)
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                LOG.warn("Claude version check timed out");
                return false;
            }

            int exitCode = process.exitValue();
            boolean installed = (exitCode == 0);

            if (installed) {
                LOG.debug("Claude is installed and available");
            } else {
                LOG.debug("Claude is not available (exit code: " + exitCode + ")");
            }

            return installed;

        } catch (IOException e) {
            LOG.debugf("Claude is not installed or not in PATH: %s", e.getMessage());
            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Claude installation check was interrupted", e);
            return false;
        }
    }
}
