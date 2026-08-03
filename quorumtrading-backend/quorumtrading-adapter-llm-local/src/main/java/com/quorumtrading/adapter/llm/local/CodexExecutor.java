package com.quorumtrading.adapter.llm.local;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Executor for the Codex CLI tool.
 * <p>
 * Executes the 'codex' command-line tool with the following format:
 * <code>codex exec --skip-git-repo-check --full-auto "&lt;prompt&gt;"</code>
 * </p>
 * <p>
 * Codex outputs JSON on stdout with metadata on stderr.
 * </p>
 *
 * @author QuorumTrading
 * @since 1.0.0
 */
@ApplicationScoped
public class CodexExecutor implements CLIExecutor {

    private static final Logger LOG = Logger.getLogger(CodexExecutor.class);
    private static final String TOOL_NAME = "codex";
    private static final long TIMEOUT_SECONDS = 120;

    /** The model used when resolution produced nothing — the historical hardcoded value. */
    private static final String DEFAULT_MODEL = "gpt-5";

    /**
     * No catalogue is advertised. codex was <b>not installed</b> on any machine this was verified
     * against, so its accepted model names are unknown — and a guessed list would let a user pin
     * something the tool rejects, failing at run time rather than at save time. Absent is the
     * honest model of an unverified capability.
     */
    @Override
    public java.util.List<String> supportedModels() {
        return java.util.List.of();
    }

    /**
     * codex takes reasoning effort as a {@code --config} value rather than a flag; the existing
     * hardcoded {@code low} is preserved when nothing is requested.
     */
    private static String effortConfig(com.quorumtrading.model.valueobject.ModelEffort effort) {
        String level = effort == null ? "low" : effort.flagValue();
        return "model_reasoning_effort=\"" + level + "\"";
    }

    @Override
    public String execute(String prompt) throws CLIExecutionException {
        return execute(prompt, null, null);
    }

    @Override
    public String execute(String prompt, String model, com.quorumtrading.model.valueobject.ModelEffort effort)
            throws CLIExecutionException {
        String requestedModel = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        return doExecute(prompt, requestedModel, effort);
    }

    private String doExecute(String prompt, String requestedModel,
                             com.quorumtrading.model.valueobject.ModelEffort effort) throws CLIExecutionException {
        if (prompt == null) {
            throw new CLIExecutionException("Prompt cannot be null");
        }

        if (prompt.isEmpty()) {
            throw new CLIExecutionException("Prompt cannot be empty");
        }

        LOG.debugf("Executing codex with model %s (prompt length: %d characters)",
                requestedModel, prompt.length());

        ProcessBuilder processBuilder = new ProcessBuilder(buildCommand(prompt, requestedModel, effort));

        // Merge error stream with output stream for easier handling
        processBuilder.redirectErrorStream(true);

        LOG.debug("Starting codex process...");
        String response = runProcess(processBuilder);

        // Check for error messages from Codex API (may exit with code 0 but contain error)
        if (isErrorResponse(response)) {
            LOG.warnf("Codex returned error message: %s", response);
            throw new CLIExecutionException(
                String.format("Codex API error: %s", response)
            );
        }

        return response;
    }

    /**
     * The exact argv handed to {@code codex}. Extracted so it can be asserted without a process.
     *
     * <p>The model is no longer the string literal {@code gpt-5}: that made the resolution chain
     * decorative on this tool. It is still DEFAULT_MODEL when nothing resolved, which preserves the
     * previous behaviour exactly.</p>
     */
    static java.util.List<String> buildCommand(String prompt, String model,
                                               com.quorumtrading.model.valueobject.ModelEffort effort) {
        return java.util.List.of(
            TOOL_NAME, "exec", "--skip-git-repo-check",
            "--config", effortConfig(effort),
            "--model", model,
            "--full-auto", prompt);
    }

    /** Seam over the child process — see {@code QwenExecutor#runProcess} for why it exists. */
    String runProcess(ProcessBuilder processBuilder) throws CLIExecutionException {
        return CliProcessRunner.run(processBuilder, TIMEOUT_SECONDS, "Codex");
    }

    /**
     * Detects if the response contains an error message from Codex API.
     * Codex may exit with code 0 but return error messages like:
     * - "ERROR: You've hit your usage limit..."
     * - "error: ..."
     * - "Exception: ..."
     */
    private boolean isErrorResponse(String response) {
        if (response == null || response.isEmpty()) {
            return false;
        }

        String lowerResponse = response.toLowerCase();

        // Check for common error patterns
        return lowerResponse.startsWith("error:") ||
               lowerResponse.startsWith("exception:") ||
               lowerResponse.contains("hit your usage limit") ||
               lowerResponse.contains("upgrade to pro") ||
               lowerResponse.contains("api key") ||
               lowerResponse.contains("authentication") ||
               lowerResponse.contains("forbidden") ||
               lowerResponse.contains("unauthorized");
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public boolean isInstalled() {
        try {
            // Try to execute: codex --version
            ProcessBuilder processBuilder = new ProcessBuilder(TOOL_NAME, "--version");
            processBuilder.redirectErrorStream(true);

            LOG.debug("Checking if codex is installed...");
            Process process = processBuilder.start();
            CliProcessRunner.closeStdin(process, "Codex");

            // Wait for process with a short timeout (5 seconds)
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                LOG.warn("Codex version check timed out");
                return false;
            }

            int exitCode = process.exitValue();
            boolean installed = (exitCode == 0);

            if (installed) {
                LOG.debug("Codex is installed and available");
            } else {
                LOG.debug("Codex is not available (exit code: " + exitCode + ")");
            }

            return installed;

        } catch (IOException e) {
            LOG.debugf("Codex is not installed or not in PATH: %s", e.getMessage());
            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Codex installation check was interrupted", e);
            return false;
        }
    }
}
