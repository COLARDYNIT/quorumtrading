package com.quorumtrading.adapter.llm.openrouter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.application.orchestration.agents.LLMConfig;
import com.quorumtrading.application.orchestration.agents.LLMQueryResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The other half of the zero-cost contract: OpenRouter is the one engine that <em>can</em> put a
 * price on a call, and the price it records is the provider's own reported figure — not an
 * estimate, and not a markup.
 *
 * <p>Without this, the two local-engine tests would be vacuous: "everything is free" is easy to
 * satisfy by never recording cost at all. This pins that a metered engine really does produce a
 * number, so the local zeroes mean something.</p>
 *
 * <p>The figure used is one the audited install actually paid — {@code $0.005584} for a single
 * {@code mistral-small-3.2-24b-instruct:online} call — and the raw cost is asserted
 * <strong>unmultiplied</strong>: {@code llm_usage_log} stores what the provider charged, and only
 * {@code user_budgets} holds marked-up dollars.</p>
 */
@DisplayName("OpenRouterAdapter — a metered engine records the provider's own figure")
class OpenRouterProviderCostContractTest {

    /** A real per-call cost observed in the host logs of the audited install. */
    private static final String PROVIDER_REPORTED_COST = "0.005584";

    private HttpServer server;
    private OpenRouterAdapter adapter;
    private final AtomicReference<String> responseBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/chat/completions", exchange -> {
            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();

        adapter = new OpenRouterAdapter();
        adapter.baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1";
        adapter.apiKey = "sk-test-key";
        adapter.requestTimeout = Duration.ofSeconds(10);
        adapter.fallbackModels = List.of();
        adapter.objectMapper = new ObjectMapper();
        adapter.rateLimitCache = new ModelRateLimitCache(30, 720);
        // preferencePort and modelCatalogCache stay null: the adapter guards both, and a response
        // that reports its own cost never reaches the catalogue-estimate fallback.
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private LLMQueryResult query() {
        return adapter.querySingleModelAsync(
            "analyse AAPL",
            new LLMConfig("mistralai/mistral-small-3.2-24b-instruct:online", 0.2, 512)
        ).join();
    }

    @Test
    @DisplayName("the cost reported inside usage is recorded verbatim")
    void usageCostIsRecordedVerbatim() {
        responseBody.set("""
            {
              "model": "mistralai/mistral-small-3.2-24b-instruct:online",
              "choices": [{"message": {"role": "assistant", "content": "BUY"}}],
              "usage": {
                "prompt_tokens": 1200,
                "completion_tokens": 300,
                "total_tokens": 1500,
                "cost": %s
              }
            }
            """.formatted(PROVIDER_REPORTED_COST));

        LLMQueryResult result = query();

        assertThat(result.usage().rawCost())
            .as("the provider's own figure, stored raw and unmultiplied")
            .isEqualByComparingTo(PROVIDER_REPORTED_COST);
        assertThat(result.usage().totalTokens()).isEqualTo(1500);
    }

    @Test
    @DisplayName("a provider-reported zero stays zero — a free model is not invented a price")
    void freeModelStaysFree() {
        responseBody.set("""
            {
              "model": "qwen/qwen3-coder:free",
              "choices": [{"message": {"role": "assistant", "content": "HOLD"}}],
              "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15, "cost": 0}
            }
            """);

        assertThat(query().usage().rawCost()).isEqualByComparingTo("0");
    }
}
