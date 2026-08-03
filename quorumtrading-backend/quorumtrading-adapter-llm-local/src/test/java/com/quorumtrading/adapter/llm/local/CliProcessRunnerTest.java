package com.quorumtrading.adapter.llm.local;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pipe handling for one-shot CLI invocations.
 *
 * <p>These run against a fake {@link Process}. Proving that the child's stdin gets closed by
 * starting a real binary would make the test depend on which CLIs are installed on the machine —
 * and the CLI that exposed the bug ({@code opencode}) is not installed on CI at all.</p>
 */
class CliProcessRunnerTest {

    /**
     * The regression this class exists for.
     *
     * <p>{@link StdinGatedProcess} exits only once its stdin reaches EOF, which is precisely how
     * {@code opencode run} behaves: it does {@code await Bun.stdin.text()} whenever stdin is not a
     * TTY. Against the previous implementation — which never closed the stream — this call could
     * not terminate. Delete the {@code closeStdin} call and this test fails with a timeout.</p>
     */
    @Test
    @DisplayName("closes the child's stdin, so a CLI that reads stdin sees EOF and can exit")
    void closesChildStdinSoAStdinReadingCliCanExit() throws Exception {
        StdinGatedProcess process = new StdinGatedProcess("ANSWER", "");

        String response = CliProcessRunner.consume(process, 5, "opencode");

        assertThat(process.stdinClosed()).isTrue();
        assertThat(response).isEqualTo("ANSWER");
    }

    @Test
    @DisplayName("a child that never gets EOF would time out rather than hang forever")
    void aChildDeniedEofTimesOutInsteadOfHangingForever() {
        // Same fake, but stdin closing is ignored — the child never observes EOF. This models the
        // old behaviour, and pins that the outcome is a bounded, named failure and not a hang:
        // the old code drained stdout to EOF *before* waitFor, so the timeout was unreachable.
        StdinGatedProcess process = new StdinGatedProcess("ANSWER", "");
        process.ignoreStdinClose();

        assertThatThrownBy(() -> CliProcessRunner.consume(process, 1, "opencode"))
                .isInstanceOf(CLIExecutionException.class)
                .hasMessageContaining("timed out after 1 seconds");

        assertThat(process.destroyedForcibly()).isTrue();
    }

    @Test
    @DisplayName("a non-zero exit reports what the tool said on stderr")
    void nonZeroExitReportsStderr() {
        StdinGatedProcess process = new StdinGatedProcess("", "model not found");
        process.exitWith(1);

        assertThatThrownBy(() -> CliProcessRunner.consume(process, 5, "opencode"))
                .isInstanceOf(CLIExecutionException.class)
                .hasMessageContaining("exit code 1")
                .hasMessageContaining("model not found");
    }

    /**
     * {@code opencode} reports its errors as JSON on <b>stdout</b> with an empty stderr (verified
     * on 1.18.10: exit 1, stderr empty). A failure that named only stderr would say nothing at all.
     */
    @Test
    @DisplayName("falls back to stdout when the tool failed but said nothing on stderr")
    void fallsBackToStdoutWhenStderrIsEmpty() {
        StdinGatedProcess process = new StdinGatedProcess("{\"type\":\"error\"}", "");
        process.exitWith(1);

        assertThatThrownBy(() -> CliProcessRunner.consume(process, 5, "opencode"))
                .isInstanceOf(CLIExecutionException.class)
                .hasMessageContaining("{\"type\":\"error\"}");
    }

    /**
     * Draining one stream to exhaustion before touching the other deadlocks a real child as soon as
     * it fills the untouched pipe. Both must come back whole.
     */
    @Test
    @DisplayName("drains stdout and stderr concurrently, losing neither")
    void drainsBothStreamsConcurrently() throws Exception {
        String bigStdout = "o".repeat(200_000);
        String bigStderr = "e".repeat(200_000);
        StdinGatedProcess process = new StdinGatedProcess(bigStdout, bigStderr);
        process.exitWith(1);

        assertThatThrownBy(() -> CliProcessRunner.consume(process, 5, "claude"))
                .isInstanceOf(CLIExecutionException.class)
                .hasMessageContaining(bigStderr);
    }

    /**
     * A tool that is not on PATH must fail with a message that names it and says what to check.
     * This is the single most common local-CLI failure and the one users self-diagnose from.
     */
    @Test
    @DisplayName("a missing binary fails naming the tool, not with a raw IOException")
    void aMissingBinaryFailsHelpfully() {
        ProcessBuilder builder =
                new ProcessBuilder("quorumtrading-no-such-cli-binary-cbf1f0", "--version");

        assertThatThrownBy(() -> CliProcessRunner.run(builder, 5, "Opencode"))
                .isInstanceOf(CLIExecutionException.class)
                .hasMessageContaining("Failed to execute opencode command")
                .hasMessageContaining("installed and in PATH");
    }

    /** A fake child that mirrors the CLI behaviour that caused the hang. */
    private static final class StdinGatedProcess extends Process {

        private final AtomicBoolean stdinClosed = new AtomicBoolean(false);
        private final AtomicBoolean destroyedForcibly = new AtomicBoolean(false);
        private final InputStream stdout;
        private final InputStream stderr;
        private boolean honourStdinClose = true;
        private int exitCode;

        private StdinGatedProcess(String stdoutText, String stderrText) {
            this.stdout = new ByteArrayInputStream(stdoutText.getBytes(StandardCharsets.UTF_8));
            this.stderr = new ByteArrayInputStream(stderrText.getBytes(StandardCharsets.UTF_8));
        }

        void ignoreStdinClose() {
            this.honourStdinClose = false;
        }

        void exitWith(int code) {
            this.exitCode = code;
        }

        boolean stdinClosed() {
            return stdinClosed.get();
        }

        boolean destroyedForcibly() {
            return destroyedForcibly.get();
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream() {
                @Override
                public void close() throws IOException {
                    stdinClosed.set(true);
                    super.close();
                }
            };
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        /** Exits only once stdin has reached EOF — exactly what opencode does. */
        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return honourStdinClose && stdinClosed.get();
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public Process destroyForcibly() {
            destroyedForcibly.set(true);
            return this;
        }

        @Override
        public void destroy() {
            destroyedForcibly.set(true);
        }
    }
}
