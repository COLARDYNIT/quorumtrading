package com.quorumtrading.adapter.llm.openrouter;

import com.quorumtrading.application.orchestration.agents.LLMConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What this adapter does with the model the platform resolved for it.
 *
 * <p>Phase 0 recorded a third behaviour that is now gone: {@code QT_LLM_LOCALHTTP_MODEL} was read
 * here, inside the request path, and beat all six resolution tiers. That read has moved to
 * {@code SystemModelDefaultsProvider}, where a pin is this engine's <em>system default</em> and so
 * arrives as an ordinary requested model — honoured by {@link #advertisedRequestIsHonoured()}
 * because the server, by definition, advertises what it was pinned to. The adapter now reads
 * nothing from the environment, which is the point: one computed model, reported everywhere,
 * actually run.</p>
 *
 * <p>What remains is the substitution safety net, still silent. Turning that into an explicit
 * SERVE/SUBSTITUTE/REJECT decision is the next phase's job.</p>
 */
@DisplayName("LocalHttpLLMAdapter.resolveModel — engine-side model negotiation")
class LocalHttpResolveModelCharacterisationTest {

    private static final LLMConfig REQUEST =
            new LLMConfig("mistralai/mistral-small-3.2-24b-instruct", 0.3, 1000);

    /** Adapter with the two network probes replaced by fixtures. */
    private static LocalHttpLLMAdapter adapter(Set<String> advertised, String first) {
        return new LocalHttpLLMAdapter() {
            @Override
            Set<String> advertisedModels() {
                return advertised;
            }

            @Override
            String firstAdvertisedModel() {
                return first;
            }
        };
    }

    @Test
    @DisplayName("a requested model the server advertises is honoured — including a pin arriving via tier 6")
    void advertisedRequestIsHonoured() {
        LocalHttpLLMAdapter adapter = adapter(Set.of("llama3.2", "qwen3:8b"), "llama3.2");

        assertThat(adapter.resolveModel(new LLMConfig("qwen3:8b", 0.3, 1000))).isEqualTo("qwen3:8b");
    }

    @Test
    @DisplayName("the adapter reads no model configuration of its own — only what it was asked for")
    void adapterHoldsNoModelConfiguration() {
        assertThat(LocalHttpLLMAdapter.class.getDeclaredFields())
                .noneSatisfy(field -> assertThat(field.getName()).isEqualTo("pinnedModel"));
    }

    @Test
    @DisplayName("an unservable request is swapped for the server's first advertised model")
    void firstAdvertisedModelSubstitutes() {
        LocalHttpLLMAdapter adapter = adapter(Set.of("llama3.2"), "llama3.2");

        assertThat(adapter.resolveModel(REQUEST)).isEqualTo("llama3.2");
    }

    @Test
    @DisplayName("with nothing advertised at all, the request passes through unchanged")
    void unreachableServerPassesTheRequestThrough() {
        LocalHttpLLMAdapter adapter = adapter(Set.of(), null);

        assertThat(adapter.resolveModel(REQUEST)).isEqualTo(REQUEST.model());
    }
}
