package com.quorumtrading.adapter.llm.local;

import com.quorumtrading.model.valueobject.ModelEffort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What each CLI tool advertises about itself.
 *
 * <p>The rule these encode: a capability that was not verified on a real machine is modelled as
 * ABSENT. A guessed model list would let a user pin an id the tool rejects — failing mid-backtest
 * rather than at save time — and a guessed flag makes every call fail outright.</p>
 *
 * <p>Verified 2026-08-02: {@code claude} and {@code opencode} were installed and probed;
 * {@code codex}, {@code qwen} and {@code gemini} were not.</p>
 */
@DisplayName("CLI executors — verified capabilities only")
class CliExecutorCapabilityTest {

    @Test
    @DisplayName("claude advertises its alias vocabulary and accepts an effort flag")
    void claudeAdvertisesAliasesAndEffort() {
        ClaudeExecutor claude = new ClaudeExecutor();

        assertThat(claude.getToolName()).isEqualTo("claude");
        assertThat(claude.supportedModels()).containsExactly("haiku", "sonnet", "opus", "fable");
        assertThat(claude.supportsEffort())
                .as("--effort <low|medium|high|xhigh|max> was verified present")
                .isTrue();
    }

    @Test
    @DisplayName("codex advertises no catalogue — it was never installed to probe")
    void codexAdvertisesNothing() {
        CodexExecutor codex = new CodexExecutor();

        assertThat(codex.getToolName()).isEqualTo("codex");
        assertThat(codex.supportedModels())
                .as("an unverified model list must be absent, not guessed")
                .isEmpty();
    }

    @Test
    @DisplayName("opencode accepts an effort flag but advertises no catalogue of its own")
    void opencodeHasEffortButNoCatalogue() {
        OpencodeExecutor opencode = new OpencodeExecutor();

        assertThat(opencode.getToolName()).isEqualTo("opencode");
        assertThat(opencode.supportsEffort())
                .as("--variant was verified present")
                .isTrue();
        assertThat(opencode.supportedModels())
                .as("which provider/model pairs work depends on the user's own opencode config")
                .isEmpty();
    }

    @Test
    @DisplayName("tools with no verified flag surface advertise nothing and no effort")
    void unprobedToolsAdvertiseNothing() {
        for (CLIExecutor executor : new CLIExecutor[]{new QwenExecutor(), new GeminiExecutor()}) {
            assertThat(executor.supportedModels())
                    .as("%s was not installed when the flag surface was probed", executor.getToolName())
                    .isEmpty();
            assertThat(executor.supportsEffort()).isFalse();
        }
    }

    @Test
    @DisplayName("a null or empty prompt is refused before a process is ever started")
    void emptyPromptsAreRefused() {
        OpencodeExecutor opencode = new OpencodeExecutor();

        assertThatThrownBy(() -> opencode.execute(null, "anthropic/claude-sonnet-4.6", ModelEffort.HIGH))
                .isInstanceOf(CLIExecutionException.class)
                .hasMessageContaining("null");
        assertThatThrownBy(() -> opencode.execute("", null, null))
                .isInstanceOf(CLIExecutionException.class)
                .hasMessageContaining("empty");

        ClaudeExecutor claude = new ClaudeExecutor();
        assertThatThrownBy(() -> claude.execute(null, "sonnet", null))
                .isInstanceOf(CLIExecutionException.class);
        assertThatThrownBy(() -> claude.execute("", "sonnet", null))
                .isInstanceOf(CLIExecutionException.class);
    }

    @Test
    @DisplayName("claude puts the resolved model on the command line — it used to be the literal 'haiku'")
    void claudeCommandCarriesTheModel() {
        assertThat(ClaudeExecutor.buildCommand("prompt", "sonnet", null))
                .containsSequence("--model", "sonnet")
                .doesNotContain("--effort")
                .startsWith("claude", "-p", "prompt");
    }

    @Test
    @DisplayName("claude passes --effort only when one was asked for")
    void claudeEffortFlagIsConditional() {
        assertThat(ClaudeExecutor.buildCommand("prompt", "sonnet", ModelEffort.HIGH))
                .containsSequence("--effort", "high");
        assertThat(ClaudeExecutor.buildCommand("prompt", "sonnet", ModelEffort.XHIGH))
                .containsSequence("--effort", "xhigh");
        assertThat(ClaudeExecutor.buildCommand("prompt", "sonnet", null))
                .as("an engine must omit the flag rather than invent a level")
                .doesNotContain("--effort");
    }

    @Test
    @DisplayName("claude never passes --fallback-model — one substitution layer, not two")
    void claudeDoesNotStackASecondFallbackLayer() {
        assertThat(ClaudeExecutor.buildCommand("prompt", "sonnet", ModelEffort.MAX))
                .doesNotContain("--fallback-model");
    }

    @Test
    @DisplayName("opencode takes a namespaced provider/model id unchanged")
    void opencodeCarriesANamespacedId() {
        assertThat(OpencodeExecutor.buildCommand("prompt", "anthropic/claude-sonnet-4.6", null))
                .containsSequence("--model", "anthropic/claude-sonnet-4.6")
                .endsWith("prompt");
    }

    @Test
    @DisplayName("opencode omits the model flag entirely when nothing resolved")
    void opencodeOmitsAnAbsentModel() {
        assertThat(OpencodeExecutor.buildCommand("prompt", null, null)).doesNotContain("--model");
        assertThat(OpencodeExecutor.buildCommand("prompt", "   ", null)).doesNotContain("--model");
    }

    @Test
    @DisplayName("opencode expresses effort as --variant, its own spelling")
    void opencodeUsesVariantForEffort() {
        assertThat(OpencodeExecutor.buildCommand("prompt", "anthropic/x", ModelEffort.MAX))
                .containsSequence("--variant", "max");
        assertThat(OpencodeExecutor.buildCommand("prompt", "anthropic/x", null))
                .doesNotContain("--variant");
    }

    @Test
    @DisplayName("the default one-argument execute delegates to the model-aware one")
    void oneArgExecuteDelegates() {
        // A tool that overrides only the 3-arg form must still answer the 1-arg contract, and
        // vice versa — every executor in this module is reachable both ways.
        CLIExecutor unverified = new CLIExecutor() {
            @Override
            public String execute(String prompt) {
                return "delegated:" + prompt;
            }

            @Override
            public String getToolName() {
                return "stub";
            }

            @Override
            public boolean isInstalled() {
                return false;
            }
        };

        assertThat(unverified.execute("p", "some/model", ModelEffort.MAX)).isEqualTo("delegated:p");
        assertThat(unverified.supportedModels()).isEmpty();
        assertThat(unverified.supportsEffort()).isFalse();
    }
}
