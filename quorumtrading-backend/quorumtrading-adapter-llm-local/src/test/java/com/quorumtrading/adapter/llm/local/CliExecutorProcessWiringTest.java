package com.quorumtrading.adapter.llm.local;

import com.quorumtrading.model.valueobject.ModelEffort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What each executor actually hands to {@link ProcessBuilder}, driven through {@code execute()}.
 *
 * <p>Each test overrides the executor's {@code runProcess} seam, so the whole method runs without
 * the CLI binary being installed on the machine running the suite. The older tests that shelled out
 * for real are surefire-excluded precisely because they could not do this — and they asserted
 * almost nothing as a result.</p>
 */
class CliExecutorProcessWiringTest {

    @Test
    @DisplayName("qwen: qwen -p <prompt>")
    void qwenWiring() throws Exception {
        AtomicReference<ProcessBuilder> captured = new AtomicReference<>();
        QwenExecutor executor = new QwenExecutor() {
            @Override
            String runProcess(ProcessBuilder processBuilder) {
                captured.set(processBuilder);
                return "qwen answered";
            }
        };

        assertThat(executor.execute("decide")).isEqualTo("qwen answered");
        assertThat(captured.get().command()).containsExactly("qwen", "-p", "decide");
        assertThat(captured.get().redirectErrorStream()).isFalse();
    }

    @Test
    @DisplayName("gemini: pinned model, json output, streams kept separate")
    void geminiWiring() throws Exception {
        AtomicReference<ProcessBuilder> captured = new AtomicReference<>();
        GeminiExecutor executor = new GeminiExecutor() {
            @Override
            CliProcessRunner.CliResult runProcess(ProcessBuilder processBuilder) {
                captured.set(processBuilder);
                return new CliProcessRunner.CliResult("gemini answered", "");
            }
        };

        assertThat(executor.execute("decide")).isEqualTo("gemini answered");
        assertThat(captured.get().command())
                .containsExactly("gemini", "-m", "gemini-2.5-flash", "-p", "decide",
                        "--output-format", "json");
    }

    /** Gemini can exit 0 and still have failed; that must not reach the caller as an answer. */
    @Test
    @DisplayName("gemini: an error on stderr is a failure even when the tool exited cleanly")
    void geminiExitZeroErrorStillFails() {
        GeminiExecutor executor = new GeminiExecutor() {
            @Override
            CliProcessRunner.CliResult runProcess(ProcessBuilder processBuilder) {
                return new CliProcessRunner.CliResult("", "ERROR: quota exceeded");
            }
        };

        assertThatThrownBy(() -> executor.execute("decide"))
                .isInstanceOf(CLIExecutionException.class)
                .hasMessageContaining("quota exceeded");
    }

    @Test
    @DisplayName("codex: carries the resolved model and merges stderr into stdout")
    void codexWiring() throws Exception {
        AtomicReference<ProcessBuilder> captured = new AtomicReference<>();
        CodexExecutor executor = new CodexExecutor() {
            @Override
            String runProcess(ProcessBuilder processBuilder) {
                captured.set(processBuilder);
                return "codex answered";
            }
        };

        assertThat(executor.execute("decide", "gpt-5-codex", ModelEffort.HIGH))
                .isEqualTo("codex answered");
        assertThat(captured.get().command())
                .containsSequence("--model", "gpt-5-codex")
                .startsWith("codex", "exec", "--skip-git-repo-check")
                .endsWith("--full-auto", "decide");
        assertThat(captured.get().redirectErrorStream()).isTrue();
    }

    @Test
    @DisplayName("codex: an error message in a clean exit is still a failure")
    void codexExitZeroErrorStillFails() {
        CodexExecutor executor = new CodexExecutor() {
            @Override
            String runProcess(ProcessBuilder processBuilder) {
                return "ERROR: You've hit your usage limit";
            }
        };

        assertThatThrownBy(() -> executor.execute("decide", "gpt-5-codex", null))
                .isInstanceOf(CLIExecutionException.class)
                .hasMessageContaining("Codex API error");
    }

    @Test
    @DisplayName("claude: -p prompt, json output, resolved model and effort")
    void claudeWiring() throws Exception {
        AtomicReference<ProcessBuilder> captured = new AtomicReference<>();
        ClaudeExecutor executor = new ClaudeExecutor() {
            @Override
            String runProcess(ProcessBuilder processBuilder) {
                captured.set(processBuilder);
                return "claude answered";
            }
        };

        assertThat(executor.execute("decide", "sonnet", ModelEffort.HIGH))
                .isEqualTo("claude answered");
        assertThat(captured.get().command())
                .containsSequence("--model", "sonnet")
                .containsSequence("--effort", "high")
                .containsSequence("-p", "decide");
    }

    @Test
    @DisplayName("claude: falls back to its historical default model when nothing resolved")
    void claudeDefaultsWhenNothingResolved() throws Exception {
        AtomicReference<ProcessBuilder> captured = new AtomicReference<>();
        ClaudeExecutor executor = new ClaudeExecutor() {
            @Override
            String runProcess(ProcessBuilder processBuilder) {
                captured.set(processBuilder);
                return "ok";
            }
        };

        executor.execute("decide");

        assertThat(captured.get().command()).containsSequence("--model", "haiku");
        assertThat(captured.get().command()).doesNotContain("--effort");
    }

    @Test
    @DisplayName("opencode: runs in the pinned scratch dir, not the JVM's working directory")
    void opencodeRunsInAPinnedDirectory() throws Exception {
        AtomicReference<ProcessBuilder> captured = new AtomicReference<>();
        OpencodeExecutor executor = new OpencodeExecutor() {
            @Override
            String runProcess(ProcessBuilder processBuilder) {
                captured.set(processBuilder);
                return "opencode answered";
            }
        };

        assertThat(executor.execute("decide", "openrouter/anthropic/claude-haiku-4.5", null))
                .isEqualTo("opencode answered");

        List<String> command = captured.get().command();
        assertThat(command).containsSequence("--model", "openrouter/anthropic/claude-haiku-4.5");
        assertThat(command).contains("--dir");
        // The child's own cwd is pinned too: --dir governs opencode's project discovery, while the
        // process directory is what any tool the model runs would inherit.
        assertThat(captured.get().directory()).isNotNull();
        assertThat(captured.get().directory().toPath())
                .isEqualTo(Path.of(command.get(command.indexOf("--dir") + 1)));
        // Never the directory the backend happens to have been started from.
        assertThat(captured.get().directory().toPath())
                .isNotEqualTo(Path.of(System.getProperty("user.dir")));
    }

    @Test
    @DisplayName("opencode: honours a configured working directory and creates it")
    void opencodeHonoursConfiguredDirectory() throws Exception {
        Path configured = Files.createTempDirectory("opencode-wiring-test");
        OpencodeExecutor executor = new OpencodeExecutor();
        executor.configuredWorkingDir = Optional.of(configured.toString());

        assertThat(executor.resolveWorkingDir()).isEqualTo(configured.toString());
        assertThat(configured).exists();
    }

    @Test
    @DisplayName("opencode: an unset working directory still resolves to a scratch dir, never the cwd")
    void opencodeDefaultsToAScratchDirectory() {
        OpencodeExecutor executor = new OpencodeExecutor();
        executor.configuredWorkingDir = Optional.empty();

        String resolved = executor.resolveWorkingDir();

        assertThat(resolved).isNotNull();
        assertThat(Path.of(resolved)).exists();
        assertThat(Path.of(resolved)).isNotEqualTo(Path.of(System.getProperty("user.dir")));
    }

    @Test
    @DisplayName("opencode: a configured agent and auto-approve reach the argv")
    void opencodePassesAgentAndAutoApprove() throws Exception {
        AtomicReference<ProcessBuilder> captured = new AtomicReference<>();
        OpencodeExecutor executor = new OpencodeExecutor() {
            @Override
            String runProcess(ProcessBuilder processBuilder) {
                captured.set(processBuilder);
                return "ok";
            }
        };
        executor.configuredAgent = Optional.of("quorum-trader");
        executor.autoApprove = true;

        executor.execute("decide", null, ModelEffort.MAX);

        assertThat(captured.get().command())
                .containsSequence("--agent", "quorum-trader")
                .contains("--auto")
                .containsSequence("--variant", "max");
    }
}
