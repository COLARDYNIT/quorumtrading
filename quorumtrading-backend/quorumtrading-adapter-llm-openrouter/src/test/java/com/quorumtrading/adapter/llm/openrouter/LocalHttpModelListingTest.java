package com.quorumtrading.adapter.llm.openrouter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The local server's /models list is this deployment's model catalogue. Before these
 * overrides existed, the router fell through to the OpenRouter catalogue, so a strategy
 * pinning a real Ollama model ({@code llm_model: qwen3:8b}) was rejected with a message
 * suggesting {@code anthropic/claude-3.5-sonnet} — implying local models cannot be pinned
 * at all, when QT_LLM_LOCALHTTP_MODEL and per-strategy pins both work.
 */
@DisplayName("LocalHttpLLMAdapter — model catalogue from the server's /models list")
class LocalHttpModelListingTest {

    /** Adapter whose advertised-model probe is replaced by a fixed set. */
    private static LocalHttpLLMAdapter adapterServing(Set<String> models) {
        return new LocalHttpLLMAdapter() {
            @Override
            java.util.Set<String> advertisedModels() {
                return models;
            }
        };
    }

    @Test
    @DisplayName("a server with models supports listing; an unreachable one does not")
    void supportsListingTracksTheServer() {
        assertThat(adapterServing(Set.of("llama3.2")).supportsModelListing()).isTrue();
        assertThat(adapterServing(Set.of()).supportsModelListing()).isFalse();
    }

    @Test
    @DisplayName("advertised ids resolve, free-priced; unknown ids stay empty")
    void modelInfoComesFromTheAdvertisedList() {
        LocalHttpLLMAdapter adapter = adapterServing(Set.of("llama3.2", "qwen3:8b"));

        assertThat(adapter.getModelInfo("qwen3:8b")).isPresent();
        assertThat(adapter.getModelInfo("qwen3:8b").orElseThrow().pricing().isFree()).isTrue();
        assertThat(adapter.getModelInfo("anthropic/claude-3.5-sonnet")).isEmpty();
        assertThat(adapter.getModelInfo(null)).isEmpty();
    }

    @Test
    @DisplayName("getAvailableModels lists every advertised id, sorted")
    void availableModelsAreSorted() {
        LocalHttpLLMAdapter adapter = adapterServing(Set.of("qwen3:8b", "llama3.2"));

        assertThat(adapter.getAvailableModels())
                .extracting(m -> m.id())
                .containsExactly("llama3.2", "qwen3:8b");
    }
}
