package com.quorumtrading.adapter.llm.local;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Executor for the Qwen CLI tool.
 * <p>
 * Executes the 'qwen' command-line tool with the following format:
 * <code>qwen -p "&lt;prompt&gt;"</code>
 * </p>
 * <p>
 * Qwen outputs JSON on stdout which may contain markdown code blocks.
 * Response parsing will be handled separately by a response parser.
 * </p>
 *
 * @author QuorumTrading
 * @since 1.0.0
 */
@ApplicationScoped
public class QwenExecutor implements CLIExecutor {

    private static final Logger LOG = Logger.getLogger(QwenExecutor.class);
    private static final String TOOL_NAME = "qwen";
    private static final long TIMEOUT_SECONDS = 120;

    @Override
    public String execute(String prompt) throws CLIExecutionException {
        if (prompt == null) {
            throw new CLIExecutionException("Prompt cannot be null");
        }

        if (prompt.isEmpty()) {
            throw new CLIExecutionException("Prompt cannot be empty");
        }

        LOG.debugf("Executing qwen with prompt (length: %d characters)", prompt.length());

        ProcessBuilder processBuilder = new ProcessBuilder(buildCommand(prompt));
        processBuilder.redirectErrorStream(false);

        LOG.debug("Starting qwen process...");
        return runProcess(processBuilder);
    }

    /** The exact argv handed to {@code qwen}. Extracted so it can be asserted without a process. */
    static java.util.List<String> buildCommand(String prompt) {
        return java.util.List.of(TOOL_NAME, "-p", prompt);
    }

    /**
     * Seam over the child process.
     *
     * <p>Package-private and overridable so a test can drive {@link #execute} end to end without
     * requiring the {@code qwen} binary to be installed on the machine running the suite — the
     * older tests that shelled out for real are surefire-excluded for exactly that reason.</p>
     *
     * <p>Pipe handling — closing the child's stdin above all — lives in {@link CliProcessRunner}.</p>
     */
    String runProcess(ProcessBuilder processBuilder) throws CLIExecutionException {
        return CliProcessRunner.run(processBuilder, TIMEOUT_SECONDS, "Qwen");
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public boolean isInstalled() {
        try {
            // Try to execute: qwen --version
            ProcessBuilder processBuilder = new ProcessBuilder(TOOL_NAME, "--version");
            processBuilder.redirectErrorStream(true);

            LOG.debug("Checking if qwen is installed...");
            Process process = processBuilder.start();
            CliProcessRunner.closeStdin(process, "Qwen");

            // Wait for process with a short timeout (5 seconds)
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                LOG.warn("Qwen version check timed out");
                return false;
            }

            int exitCode = process.exitValue();
            boolean installed = (exitCode == 0);

            if (installed) {
                LOG.debug("Qwen is installed and available");
            } else {
                LOG.debug("Qwen is not available (exit code: " + exitCode + ")");
            }

            return installed;

        } catch (IOException e) {
            LOG.debugf("Qwen is not installed or not in PATH: %s", e.getMessage());
            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Qwen installation check was interrupted", e);
            return false;
        }
    }
}
