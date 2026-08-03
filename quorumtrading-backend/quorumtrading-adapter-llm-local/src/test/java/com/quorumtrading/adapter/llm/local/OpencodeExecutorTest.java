package com.quorumtrading.adapter.llm.local;

import com.quorumtrading.model.valueobject.ModelEffort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The argv {@code opencode} is given.
 *
 * <p>Flag shape only — no process is started. Every flag asserted here was read from
 * {@code opencode run --help} on 1.18.10; nothing is assumed, because a flag this tool does not
 * have fails every call at run time, in the middle of a backtest.</p>
 */
class OpencodeExecutorTest {

    @Test
    @DisplayName("pins the working directory, so the JVM's cwd cannot leak into the prompt")
    void pinsTheWorkingDirectory() {
        List<String> command =
                OpencodeExecutor.buildCommand("prompt", null, null, "/scratch/oc", null, false);

        assertThat(command).containsSequence("--dir", "/scratch/oc");
    }

    /**
     * Measured on 1.18.10: inheriting the repository as cwd cost 13,800 input tokens against 6,230
     * for an empty directory, on an identical nine-word prompt. The flag is the fix.
     */
    @Test
    @DisplayName("omits --dir when no directory could be resolved, rather than inventing one")
    void omitsAnAbsentWorkingDirectory() {
        assertThat(OpencodeExecutor.buildCommand("prompt", null, null, null, null, false))
                .doesNotContain("--dir");
        assertThat(OpencodeExecutor.buildCommand("prompt", null, null, "  ", null, false))
                .doesNotContain("--dir");
    }

    @Test
    @DisplayName("passes --agent when a persona is configured")
    void passesTheConfiguredAgent() {
        assertThat(OpencodeExecutor.buildCommand("prompt", null, null, null, "quorum-trader", false))
                .containsSequence("--agent", "quorum-trader");
    }

    /**
     * opencode's own default agent is {@code build}, a coding assistant. Naming an agent the user
     * has not created fails every call, and this platform will not write into the user's
     * {@code ~/.config/opencode/} to create one — so an unset persona omits the flag.
     */
    @Test
    @DisplayName("omits --agent when no persona is configured")
    void omitsAnAbsentAgent() {
        assertThat(OpencodeExecutor.buildCommand("prompt", null, null, null, null, false))
                .doesNotContain("--agent");
        assertThat(OpencodeExecutor.buildCommand("prompt", null, null, null, "   ", false))
                .doesNotContain("--agent");
    }

    @Test
    @DisplayName("--auto is opt-in, never the default")
    void autoApproveIsOptIn() {
        assertThat(OpencodeExecutor.buildCommand("prompt", null, null, null, null, false))
                .doesNotContain("--auto");
        assertThat(OpencodeExecutor.buildCommand("prompt", null, null, null, null, true))
                .contains("--auto");
    }

    @Test
    @DisplayName("the prompt stays the final argument once every flag is present")
    void thePromptRemainsLast() {
        List<String> command = OpencodeExecutor.buildCommand(
                "decide on AAPL", "openrouter/anthropic/claude-haiku-4.5", ModelEffort.HIGH,
                "/scratch/oc", "quorum-trader", true);

        assertThat(command).endsWith("decide on AAPL");
        assertThat(command).startsWith("opencode", "run", "--format", "json");
        assertThat(command).containsSequence("--model", "openrouter/anthropic/claude-haiku-4.5");
        assertThat(command).containsSequence("--variant", "high");
    }

    @Test
    @DisplayName("an empty or null prompt is refused before a process is started")
    void emptyPromptsAreRefused() {
        OpencodeExecutor executor = new OpencodeExecutor();

        assertThatThrownBy(() -> executor.execute(null))
                .isInstanceOf(CLIExecutionException.class)
                .hasMessageContaining("cannot be null");
        assertThatThrownBy(() -> executor.execute(""))
                .isInstanceOf(CLIExecutionException.class)
                .hasMessageContaining("cannot be empty");
    }

    @Test
    @DisplayName("advertises effort support but no model catalogue")
    void advertisesEffortButNoCatalogue() {
        OpencodeExecutor executor = new OpencodeExecutor();

        assertThat(executor.supportsEffort()).isTrue();
        assertThat(executor.supportedModels()).isEmpty();
        assertThat(executor.getToolName()).isEqualTo("opencode");
    }
}
