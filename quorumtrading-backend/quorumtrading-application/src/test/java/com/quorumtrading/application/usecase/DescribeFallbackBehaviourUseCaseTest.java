package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.ActiveLlmEnginePort;
import com.quorumtrading.model.enums.LlmProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Fallback" is not one concept, and rendering one engine's version of it as universal is how a
 * self-hoster on Ollama came to be shown four OpenRouter models, not one of which could be served.
 */
@DisplayName("DescribeFallbackBehaviourUseCase — fallback means different things per engine")
class DescribeFallbackBehaviourUseCaseTest {

    private static final List<String> OPENROUTER_CHAIN =
            List.of("mistralai/mistral-small-3.2-24b-instruct", "meta-llama/llama-4-scout");

    private static DescribeFallbackBehaviourUseCase forEngine(LlmProvider engine, List<String> installedTools) {
        ActiveLlmEnginePort port = () -> Optional.ofNullable(engine);
        return new DescribeFallbackBehaviourUseCase(port, OPENROUTER_CHAIN, () -> installedTools);
    }

    @Test
    @DisplayName("OpenRouter has a cross-MODEL chain — the only engine where the model list is right")
    void openRouterHasAModelChain() {
        var behaviour = forEngine(LlmProvider.OPENROUTER, List.of()).execute();

        assertThat(behaviour.kind()).isEqualTo(DescribeFallbackBehaviourUseCase.Kind.MODEL_CHAIN);
        assertThat(behaviour.entries()).isEqualTo(OPENROUTER_CHAIN);
        assertThat(behaviour.engine()).isEqualTo(LlmProvider.OPENROUTER);
    }

    @Test
    @DisplayName("the CLI engine has a cross-TOOL chain, and only tools that are installed")
    void cliHasAToolChain() {
        var behaviour = forEngine(LlmProvider.LOCAL, List.of("claude")).execute();

        assertThat(behaviour.kind()).isEqualTo(DescribeFallbackBehaviourUseCase.Kind.TOOL_CHAIN);
        assertThat(behaviour.entries()).containsExactly("claude");
        assertThat(behaviour.entries())
                .as("a configured-but-absent tool must not be advertised")
                .doesNotContain("qwen");
        assertThat(behaviour.explanation()).contains("tool chain");
    }

    @Test
    @DisplayName("a local HTTP server has no chain at all, and says so instead of showing a foreign one")
    void localHttpHasNoChain() {
        var behaviour = forEngine(LlmProvider.LOCAL_HTTP, List.of()).execute();

        assertThat(behaviour.kind()).isEqualTo(DescribeFallbackBehaviourUseCase.Kind.SERVER_DEFAULT);
        assertThat(behaviour.entries()).isEmpty();
        assertThat(behaviour.explanation()).contains("no fallback chain");
    }

    @Test
    @DisplayName("no engine selected means nothing to describe")
    void noEngineMeansNothing() {
        var behaviour = forEngine(null, List.of()).execute();

        assertThat(behaviour.kind()).isEqualTo(DescribeFallbackBehaviourUseCase.Kind.NONE);
        assertThat(behaviour.engine()).isNull();
        assertThat(behaviour.entries()).isEmpty();
    }

    @Test
    @DisplayName("no engine ever claims cross-ENGINE failover — the router returns one provider by design")
    void neverClaimsCrossEngineFailover() {
        for (LlmProvider engine : LlmProvider.values()) {
            assertThat(forEngine(engine, List.of("claude")).execute().crossEngineFailover())
                    .as("%s must not imply the platform falls through to another engine", engine)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("an engine lookup that throws is 'unknown', not a crash")
    void engineLookupFailureIsSurvivable() {
        ActiveLlmEnginePort throwing = () -> {
            throw new IllegalStateException("db closed");
        };
        var behaviour = new DescribeFallbackBehaviourUseCase(throwing, OPENROUTER_CHAIN, List::of).execute();

        assertThat(behaviour.kind()).isEqualTo(DescribeFallbackBehaviourUseCase.Kind.NONE);
    }

    @Test
    @DisplayName("a null tools supplier is treated as no tools rather than exploding")
    void nullSupplierIsSafe() {
        var behaviour = new DescribeFallbackBehaviourUseCase(
                () -> Optional.of(LlmProvider.LOCAL), null, null).execute();

        assertThat(behaviour.entries()).isEmpty();
    }
}
