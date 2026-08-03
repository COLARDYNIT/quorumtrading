package com.quorumtrading.adapter.llm.openrouter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.adapter.llm.openrouter.LocalHttpLLMAdapter.LocalModelMetadata;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real context windows for local models.
 *
 * <p>Every local model was reported with {@code contextLength: 0}, which is a number in a field
 * that reads like a measurement. It made it impossible to reason about whether a model even fits
 * the agent prompts — which run long once news and sentiment context are attached — and it made the
 * LIVE_TRADING context gate meaningless for local engines.</p>
 *
 * <p>Ollama answers this through {@code /api/show}. Servers that speak only the OpenAI-compatible
 * subset (LM Studio, llama.cpp, vLLM) do not, and must degrade to "unknown" without ever failing a
 * listing — a catalogue that throws because a metadata lookup failed is strictly worse than one
 * with blanks in it.</p>
 */
@DisplayName("LocalHttpLLMAdapter — per-model metadata from Ollama's /api/show")
class LocalHttpModelMetadataTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A realistic Ollama /api/show body, architecture-namespaced exactly as Ollama emits it. */
    private static final String LLAMA_SHOW = """
            {
              "details": {
                "family": "llama",
                "parameter_size": "3.2B",
                "quantization_level": "Q4_K_M"
              },
              "model_info": {
                "general.architecture": "llama",
                "general.parameter_count": 3212749888,
                "llama.context_length": 131072,
                "llama.embedding_length": 3072
              }
            }
            """;

    /**
     * An adapter serving a fixed model list, whose {@code /api/show} probe is replaced by a counted
     * fixture so both the parsed result and the number of round-trips can be asserted.
     */
    private static LocalHttpLLMAdapter adapter(Set<String> models, AtomicInteger probes,
                                               java.util.function.Function<String, LocalModelMetadata> probe) {
        LocalHttpLLMAdapter adapter = new LocalHttpLLMAdapter() {
            @Override
            Set<String> advertisedModels() {
                return models;
            }

            @Override
            LocalModelMetadata probeModelMetadata(String modelId) {
                probes.incrementAndGet();
                return probe.apply(modelId);
            }
        };
        // Normally CDI-injected; supplied here so the parsing path is exercised for real.
        adapter.objectMapper = MAPPER;
        adapter.baseUrl = "http://localhost:11434/v1";
        return adapter;
    }

    @Nested
    @DisplayName("a server that answers /api/show")
    class ServerAnswers {

        @Test
        @DisplayName("the real context window and parameter size reach the catalogue")
        void metadataReachesTheCatalogue() throws Exception {
            LocalModelMetadata parsed = LocalHttpLLMAdapter.parseShowResponse(
                    MAPPER.readValue(LLAMA_SHOW, Map.class));
            LocalHttpLLMAdapter adapter =
                    adapter(Set.of("llama3.2"), new AtomicInteger(), id -> parsed);

            List<LLMModelInfo> models = adapter.getAvailableModels();

            assertThat(models).hasSize(1);
            assertThat(models.get(0).contextLength())
                    .as("this is the number that makes 'does the prompt fit?' answerable")
                    .isEqualTo(131072);
            assertThat(models.get(0).parameterSizeB()).isEqualTo(3);
        }

        @Test
        @DisplayName("getModelInfo carries the same metadata as the listing")
        void getModelInfoCarriesMetadata() throws Exception {
            LocalModelMetadata parsed = LocalHttpLLMAdapter.parseShowResponse(
                    MAPPER.readValue(LLAMA_SHOW, Map.class));
            LocalHttpLLMAdapter adapter =
                    adapter(Set.of("llama3.2"), new AtomicInteger(), id -> parsed);

            assertThat(adapter.getModelInfo("llama3.2")).isPresent().get()
                    .satisfies(info -> {
                        assertThat(info.contextLength()).isEqualTo(131072);
                        assertThat(info.parameterSizeB()).isEqualTo(3);
                    });
        }

        @Test
        @DisplayName("context length is found whatever architecture namespaces it")
        void contextLengthIsFoundPerArchitecture() throws Exception {
            for (String architecture : List.of("llama", "qwen3", "gemma3", "some-future-arch")) {
                String body = """
                        {"model_info": {"%s.context_length": 40960}}
                        """.formatted(architecture);

                assertThat(LocalHttpLLMAdapter.parseShowResponse(MAPPER.readValue(body, Map.class))
                        .contextLength())
                        .as("a hardcoded key list would go stale with every new model family")
                        .isEqualTo(40960);
            }
        }

        @Test
        @DisplayName("parameter count is preferred, with the display string as a fallback")
        void parameterCountPreferredOverDisplayString() throws Exception {
            String exact = """
                    {"details": {"parameter_size": "999B"},
                     "model_info": {"general.parameter_count": 70000000000}}
                    """;
            assertThat(LocalHttpLLMAdapter.parseShowResponse(MAPPER.readValue(exact, Map.class))
                    .parameterSizeB()).isEqualTo(70);

            String displayOnly = """
                    {"details": {"parameter_size": "8B"}}
                    """;
            assertThat(LocalHttpLLMAdapter.parseShowResponse(MAPPER.readValue(displayOnly, Map.class))
                    .parameterSizeB()).isEqualTo(8);
        }

        @ParameterizedTest
        @CsvSource({"3.2B,3", "70B,70", "8b,8", "540M,1", "1.5B,2"})
        @DisplayName("parameter-size strings parse to billions")
        void parameterSizeStringsParse(String raw, int expected) {
            assertThat(LocalHttpLLMAdapter.parseParameterSize(raw)).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "unknown", "huge", "B"})
        @DisplayName("an unparseable parameter size is unknown, not a guess")
        void unparseableParameterSizeIsNull(String raw) {
            assertThat(LocalHttpLLMAdapter.parseParameterSize(raw)).isNull();
        }

        @Test
        @DisplayName("a body with no model_info at all yields unknown rather than throwing")
        void emptyBodyYieldsUnknown() {
            LocalModelMetadata parsed = LocalHttpLLMAdapter.parseShowResponse(Map.of());

            assertThat(parsed.contextLength()).isZero();
            assertThat(parsed.parameterSizeB()).isNull();
        }
    }

    @Nested
    @DisplayName("a server that does not implement /api/show")
    class ServerDoesNotAnswer {

        @Test
        @DisplayName("the listing still works, with metadata reported as unknown")
        void listingStillWorks() {
            LocalHttpLLMAdapter adapter =
                    adapter(Set.of("llama3.2", "qwen3:8b"), new AtomicInteger(), id -> null);

            List<LLMModelInfo> models = adapter.getAvailableModels();

            assertThat(models).extracting(LLMModelInfo::id).containsExactly("llama3.2", "qwen3:8b");
            assertThat(models).allSatisfy(m -> {
                assertThat(m.contextLength()).isZero();
                assertThat(m.parameterSizeB()).isNull();
            });
        }

        @ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(ints = {404, 405, 501})
        @DisplayName("a 404/405/501 is remembered — one probe for the whole server, not one per model")
        void unsupportedEndpointIsRememberedOnce(int statusCode) throws Exception {
            // Having learned the endpoint is absent, no further model triggers a round-trip.
            AtomicInteger probes = new AtomicInteger();
            LocalHttpLLMAdapter counting = adapter(Set.of("a", "b", "c"), probes, id -> null);
            assertThat(counting.interpretShowResponse("a", statusCode, "")).isNull();
            counting.getAvailableModels();

            assertThat(probes.get())
                    .as("a server that does not implement /api/show must cost one probe, not three")
                    .isZero();
        }

        @Test
        @DisplayName("a transient non-200 concludes nothing permanent, so the next call retries")
        void transientErrorIsNotRemembered() throws Exception {
            AtomicInteger probes = new AtomicInteger();
            LocalHttpLLMAdapter adapter = adapter(Set.of("a"), probes, id -> null);

            // 503 while a model loads: unknown for now, not unknown forever.
            assertThat(adapter.interpretShowResponse("a", 503, "")).isNull();
            adapter.getAvailableModels();

            assertThat(probes.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("a 200 is parsed, and marks the endpoint as supported")
        void okIsParsed() throws Exception {
            LocalHttpLLMAdapter adapter = adapter(Set.of("llama3.2"), new AtomicInteger(), id -> null);

            LocalModelMetadata parsed = adapter.interpretShowResponse("llama3.2", 200, LLAMA_SHOW);

            assertThat(parsed).isNotNull();
            assertThat(parsed.contextLength()).isEqualTo(131072);
            assertThat(parsed.parameterSizeB()).isEqualTo(3);
        }

        @Test
        @DisplayName("a probe that throws never propagates out of the listing")
        void probeFailureNeverFailsTheListing() {
            LocalHttpLLMAdapter adapter = adapter(Set.of("llama3.2"), new AtomicInteger(), id -> {
                throw new IllegalStateException("connection refused");
            });

            // The real probe swallows its own exceptions; this asserts the contract the catalogue
            // depends on — a metadata lookup must never be able to break model listing.
            assertThat(adapter.metadataFor("llama3.2"))
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("caching")
    class Caching {

        @Test
        @DisplayName("a successful probe is cached — one round-trip per model, not one per call")
        void successIsCached() {
            AtomicInteger probes = new AtomicInteger();
            LocalHttpLLMAdapter adapter = adapter(Set.of("llama3.2"), probes,
                    id -> new LocalModelMetadata(131072, 3, "llama"));

            adapter.getAvailableModels();
            adapter.getAvailableModels();
            adapter.getModelInfo("llama3.2");

            assertThat(probes.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("a failed probe is NOT cached — the same property advertisedModels() holds")
        void failureIsNotCached() {
            AtomicInteger probes = new AtomicInteger();
            LocalHttpLLMAdapter adapter = adapter(Set.of("llama3.2"), probes, id -> null);

            adapter.getAvailableModels();
            adapter.getAvailableModels();

            assertThat(probes.get())
                    .as("a transient failure must not permanently pin a model's metadata to unknown")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("each model is probed once, independently")
        void eachModelProbedOnce() {
            AtomicInteger probes = new AtomicInteger();
            LocalHttpLLMAdapter adapter = adapter(Set.of("llama3.2", "qwen3:8b", "gemma3:12b"), probes,
                    id -> new LocalModelMetadata(8192, 8, id));

            adapter.getAvailableModels();
            adapter.getAvailableModels();

            assertThat(probes.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("refreshModelCatalog drops the metadata cache too, not only the id list")
        void refreshClearsMetadata() {
            AtomicInteger probes = new AtomicInteger();
            LocalHttpLLMAdapter adapter = adapter(Set.of("llama3.2"), probes,
                    id -> new LocalModelMetadata(131072, 3, "llama"));

            adapter.getAvailableModels();
            adapter.refreshModelCatalog();
            adapter.getAvailableModels();

            assertThat(probes.get()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("endpoint location")
    class EndpointLocation {

        @ParameterizedTest
        @CsvSource({
            "http://host.docker.internal:11434/v1, http://host.docker.internal:11434",
            "http://localhost:11434/v1/,          http://localhost:11434",
            "http://localhost:1234,               http://localhost:1234"
        })
        @DisplayName("/api/show sits at the server root, beside /v1 rather than under it")
        void nativeApiRootStripsTheOpenAiPrefix(String baseUrl, String expectedRoot) {
            LocalHttpLLMAdapter adapter = new LocalHttpLLMAdapter();
            adapter.baseUrl = baseUrl;

            assertThat(adapter.nativeApiRoot()).isEqualTo(expectedRoot);
        }
    }
}
