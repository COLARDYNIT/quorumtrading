package com.quorumtrading.adapter.llm.local;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs a one-shot CLI child process and returns its stdout.
 *
 * <p>Every executor in this package used to carry its own copy of this logic, and every copy had
 * the same three pipe defects. They are fixed once, here.</p>
 *
 * <h2>1. The child's stdin is closed immediately</h2>
 *
 * <p>{@link ProcessBuilder} hands a child a <em>pipe</em> for stdin. If nothing ever writes to it
 * and nothing ever closes it, the child sees a stdin that is not a TTY and never reaches EOF.</p>
 *
 * <p>A CLI that reads stdin when it is not a TTY then blocks forever. {@code opencode run} does
 * exactly this — {@code process.stdin.isTTY ? undefined : await Bun.stdin.text()} — so it waited on
 * an EOF that this process was never going to deliver. Measured on opencode 1.18.10: the identical
 * invocation returns in 0s with stdin at EOF and produces zero bytes in 30s with the pipe held open.
 *
 * <p>The other CLIs here pass their prompt as an argv element and happen not to read stdin today.
 * That is luck, not a contract — {@code claude --bare} already changed behaviour between versions —
 * so every one-shot invocation in this package closes stdin. There is nothing to send: the prompt
 * is an argument.</p>
 *
 * <p>{@code Redirect.INHERIT} is deliberately not used: the JVM's own stdin is not a TTY in a
 * container or under a daemon, which reintroduces the same hang one level up. {@code /dev/null}
 * redirection is deliberately not used either: the path is not portable to Windows.</p>
 *
 * <h2>2. The timeout is now reachable</h2>
 *
 * <p>The old shape was {@code drain(stdout); drain(stderr); waitFor(timeout)}. {@code drain} blocks
 * until the stream reaches EOF, which only happens when the child exits — so a child that hung
 * blocked the caller <em>forever</em> and the timeout below it was never evaluated. The
 * "120 second timeout" could not fire. Streams are now pumped on separate threads and
 * {@link Process#waitFor(long, TimeUnit)} is what bounds the call.</p>
 *
 * <h2>3. stdout and stderr are drained concurrently</h2>
 *
 * <p>Reading one to exhaustion before touching the other deadlocks whenever the child fills the
 * other pipe's buffer (~64 KiB) — the child blocks writing, the parent blocks reading, neither
 * moves. Both are pumped at once.</p>
 */
final class CliProcessRunner {

    private static final Logger LOG = Logger.getLogger(CliProcessRunner.class);

    /** How long to wait for the stream pumps to finish after the child has already exited. */
    private static final long PUMP_JOIN_MILLIS = 5_000;

    /**
     * Cap on how much child output goes into a log line. A failing CLI can emit hundreds of
     * kilobytes, and the whole of it in a single WARN is an operational problem of its own. The
     * exception message still carries the untruncated text to the caller.
     */
    private static final int LOGGED_OUTPUT_LIMIT = 2_000;

    private CliProcessRunner() {
        // Utility class.
    }

    /**
     * Start the command and return its trimmed stdout.
     *
     * @param builder        the command to run
     * @param timeoutSeconds hard upper bound on the child's lifetime
     * @param toolLabel      tool name, used for log and error text so a failure names its source
     * @throws CLIExecutionException if the child cannot be started, times out, or exits non-zero
     */
    static String run(ProcessBuilder builder, long timeoutSeconds, String toolLabel)
            throws CLIExecutionException {
        return runCapturing(builder, timeoutSeconds, toolLabel).stdout();
    }

    /**
     * As {@link #run}, but also returns stderr.
     *
     * <p>Needed by tools that exit 0 and report the failure in their output anyway — Gemini
     * inspects both streams for that.</p>
     */
    static CliResult runCapturing(ProcessBuilder builder, long timeoutSeconds, String toolLabel)
            throws CLIExecutionException {
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            String binary = toolLabel.toLowerCase(java.util.Locale.ROOT);
            String message = String.format(
                    "Failed to execute %s command. Is %s installed and in PATH? Error: %s",
                    binary, binary, e.getMessage());
            LOG.errorf(e, "%s", message);
            throw new CLIExecutionException(message, e);
        }
        return consumeCapturing(process, timeoutSeconds, toolLabel);
    }

    /** What a finished child said. */
    record CliResult(String stdout, String stderr) {
    }

    /**
     * Drive an already-started child to completion.
     *
     * <p>Split from {@link #run} so the pipe handling can be asserted against a fake
     * {@link Process} — starting a real binary to prove that stdin gets closed would make the test
     * depend on which CLIs happen to be installed on the machine running it.</p>
     */
    static String consume(Process process, long timeoutSeconds, String toolLabel)
            throws CLIExecutionException {
        return consumeCapturing(process, timeoutSeconds, toolLabel).stdout();
    }

    /** As {@link #consume}, but also returns stderr. */
    static CliResult consumeCapturing(Process process, long timeoutSeconds, String toolLabel)
            throws CLIExecutionException {
        closeStdin(process, toolLabel);

        AtomicReference<String> stdout = new AtomicReference<>("");
        AtomicReference<String> stderr = new AtomicReference<>("");
        Thread stdoutPump = pump(process.getInputStream(), stdout, toolLabel + "-stdout");
        Thread stderrPump = pump(process.getErrorStream(), stderr, toolLabel + "-stderr");

        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new CLIExecutionException(String.format(
                        "%s execution timed out after %d seconds", toolLabel, timeoutSeconds));
            }
            // The child has exited, so both pipes are at EOF and these joins are bounded in
            // practice; the timeout is belt-and-braces against a grandchild holding a pipe open.
            stdoutPump.join(PUMP_JOIN_MILLIS);
            stderrPump.join(PUMP_JOIN_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new CLIExecutionException(toolLabel + " execution was interrupted", e);
        }

        String response = stdout.get().trim();
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String errorMsg = stderr.get().trim();
            LOG.warnf("%s process exited with code %d. Stdout: %s, Stderr: %s",
                    toolLabel, exitCode, truncateForLog(response), truncateForLog(errorMsg));
            throw new CLIExecutionException(String.format(
                    "%s execution failed with exit code %d: %s",
                    toolLabel, exitCode, errorMsg.isEmpty() ? response : errorMsg));
        }

        LOG.debugf("%s execution successful. Response length: %d characters",
                toolLabel, response.length());
        return new CliResult(response, stderr.get().trim());
    }

    /**
     * Deliver EOF to the child.
     *
     * <p>This single call is the difference between a working one-shot invocation and an
     * indefinite hang on any CLI that reads stdin when it is not a TTY.</p>
     */
    static void closeStdin(Process process, String toolLabel) {
        try {
            process.getOutputStream().close();
        } catch (IOException e) {
            // Nothing actionable: the child may already have exited, which also delivers EOF.
            LOG.debugf("Could not close %s stdin (child may have exited): %s",
                    toolLabel, e.getMessage());
        }
    }

    /** Keep a log line to a sane size; the caller still receives the full text. */
    private static String truncateForLog(String text) {
        if (text.length() <= LOGGED_OUTPUT_LIMIT) {
            return text;
        }
        return text.substring(0, LOGGED_OUTPUT_LIMIT)
                + "... [" + (text.length() - LOGGED_OUTPUT_LIMIT) + " more characters]";
    }

    /** Read a stream to exhaustion on its own daemon thread, so neither pipe can block the other. */
    private static Thread pump(InputStream stream, AtomicReference<String> sink, String threadName) {
        Thread thread = new Thread(() -> {
            try (InputStream source = stream) {
                sink.set(new String(source.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                LOG.debugf("Failed reading %s: %s", threadName, e.getMessage());
            }
        }, threadName);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
