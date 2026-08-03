package com.quorumtrading.adapter.llm.local;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Executor for the Gemini CLI tool.
 * <p>
 * Executes the 'gemini' command-line tool with the following format:
 * <code>gemini -m gemini-2.5-flash -p "&lt;prompt&gt;" --output-format json</code>
 * </p>
 * <p>
 * Gemini outputs JSON on stdout with nested response structure.
 * Response parsing (extracting actual response from nested JSON) will be handled by ResponseParser.
 * </p>
 *
 * @author QuorumTrading
 * @since 1.0.0
 */
@ApplicationScoped
public class GeminiExecutor implements CLIExecutor {

    private static final Logger LOG = Logger.getLogger(GeminiExecutor.class);
    private static final String TOOL_NAME = "gemini";
    private static final long TIMEOUT_SECONDS = 120;
    private static final String MODEL = "gemini-2.5-flash";

    @Override
    public String execute(String prompt) throws CLIExecutionException {
        if (prompt == null) {
            throw new CLIExecutionException("Prompt cannot be null");
        }

        if (prompt.isEmpty()) {
            throw new CLIExecutionException("Prompt cannot be empty");
        }

        LOG.debugf("Executing gemini with prompt (length: %d characters)", prompt.length());

        ProcessBuilder processBuilder = new ProcessBuilder(buildCommand(prompt));
        // Keep the streams separate: the exit-0 error check below reads them individually.
        processBuilder.redirectErrorStream(false);

        LOG.debug("Starting gemini process...");
        CliProcessRunner.CliResult result = runProcess(processBuilder);
        String response = result.stdout();
        String errorMsg = result.stderr();

        // Check for error messages from Gemini API (may exit with code 0 but contain error)
        if (isErrorResponse(response) || isErrorResponse(errorMsg)) {
            LOG.warnf("Gemini returned error message: stdout=%s, stderr=%s", response, errorMsg);
            String errorContent = !errorMsg.isEmpty() ? errorMsg : response;
            throw new CLIExecutionException(
                String.format("Gemini API error: %s", errorContent)
            );
        }

        LOG.debugf("Gemini execution successful. Response length: %d characters", response.length());
        return response;
    }

    /** The exact argv handed to {@code gemini}. Extracted so it can be asserted without a process. */
    static java.util.List<String> buildCommand(String prompt) {
        return java.util.List.of(TOOL_NAME, "-m", MODEL, "-p", prompt, "--output-format", "json");
    }

    /**
     * Seam over the child process — see {@code QwenExecutor#runProcess} for why it exists.
     *
     * <p>Returns both streams: the exit-0 error check reads them individually.</p>
     */
    CliProcessRunner.CliResult runProcess(ProcessBuilder processBuilder) throws CLIExecutionException {
        return CliProcessRunner.runCapturing(processBuilder, TIMEOUT_SECONDS, "Gemini");
    }

    /**
     * Detects if the response contains an error message from Gemini API.
     * Gemini may exit with code 0 but return error messages like:
     * - "ERROR: ..."
     * - "error: ..."
     * - "Exception: ..."
     * - API rate limit or authentication errors
     */
    private boolean isErrorResponse(String response) {
        if (response == null || response.isEmpty()) {
            return false;
        }

        String lowerResponse = response.toLowerCase();

        // Check for common error patterns
        return lowerResponse.startsWith("error:") ||
               lowerResponse.startsWith("exception:") ||
               lowerResponse.contains("api error") ||
               lowerResponse.contains("rate limit") ||
               lowerResponse.contains("quota") ||
               lowerResponse.contains("authentication") ||
               lowerResponse.contains("forbidden") ||
               lowerResponse.contains("unauthorized") ||
               lowerResponse.contains("invalid api key");
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public boolean isInstalled() {
        try {
            // Try to execute: gemini --version
            ProcessBuilder processBuilder = new ProcessBuilder(TOOL_NAME, "--version");
            processBuilder.redirectErrorStream(true);

            LOG.debug("Checking if gemini is installed...");
            Process process = processBuilder.start();
            CliProcessRunner.closeStdin(process, "Gemini");

            // Wait for process with a short timeout (5 seconds)
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                LOG.warn("Gemini version check timed out");
                return false;
            }

            int exitCode = process.exitValue();
            boolean installed = (exitCode == 0);

            if (installed) {
                LOG.debug("Gemini is installed and available");
            } else {
                LOG.debug("Gemini is not available (exit code: " + exitCode + ")");
            }

            return installed;

        } catch (IOException e) {
            LOG.debugf("Gemini is not installed or not in PATH: %s", e.getMessage());
            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Gemini installation check was interrupted", e);
            return false;
        }
    }
}
