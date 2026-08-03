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
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The zero-marginal-cost guarantee for locally served inference, stated as a test rather than as
 * a comment.
 *
 * <p>A self-hosted install runs the model on the user's own hardware: no provider invoices it, so
 * no call served that way may ever acquire a price. The whole "why does my free local engine
 * report $0.15 of spend?" investigation turned on this being true — it concluded the recorded
 * spend was genuine OpenRouter usage precisely because no local code path can price a call.</p>
 *
 * <p>The fake server here does not merely omit a cost — it <strong>actively reports one</strong>,
 * in both of the shapes a provider might use. A local server (or a proxy in front of one) is free
 * to send whatever it likes; the adapter must not believe it.</p>
 */
@DisplayName("LocalHttpLLMAdapter — locally served inference is always $0")
class LocalHttpZeroCostContractTest {

    /** A cost a misbehaving local server might report. If it ever lands in the ledger, this fails. */
    private static final String FABRICATED_COST = "9.99";

    private HttpServer server;
    private LocalHttpLLMAdapter adapter;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            // An OpenAI-shaped reply claiming a cost at both levels providers use.
            String body = """
                {
                  "model": "llama3.2",
                  "cost": %s,
                  "choices": [{"message": {"role": "assistant", "content": "BUY"}}],
                  "usage": {
                    "prompt_tokens": 1200,
                    "completion_tokens": 300,
                    "total_tokens": 1500,
                    "cost": %s
                  }
                }
                """.formatted(FABRICATED_COST, FABRICATED_COST);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();

        adapter = new LocalHttpLLMAdapter();
        adapter.baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        adapter.apiKey = "not-configured";
        // No adapter-level pin any more: QT_LLM_LOCALHTTP_MODEL is system-level configuration and
        // resolves in SystemModelDefaultsProvider, so a pin reaches this adapter as an ordinary
        // requested model. Each test below already asks for llama3.2 through its LLMConfig.
        adapter.requestTimeout = Duration.ofSeconds(10);
        adapter.objectMapper = new ObjectMapper();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** A prompt long enough that 1,200 reported prompt tokens reads as a genuine measurement. */
    private static String realisticPrompt() {
        return "Analyse AAPL. ".repeat(400);
    }

    @Test
    @DisplayName("a cost reported by the local server is not believed — raw cost stays zero")
    void reportedCostIsIgnored() {
        LLMQueryResult result =
            adapter.queryWithUsage(realisticPrompt(), new LLMConfig("llama3.2", 0.2, 512));

        assertThat(result.usage().rawCost())
            .as("local inference has no marginal cost, whatever the server claims")
            .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("zero raw cost stays zero once billed — the markup floor does not lift it")
    void zeroCostSurvivesTheMarkup() {
        LLMQueryResult result =
            adapter.queryWithUsage(realisticPrompt(), new LLMConfig("llama3.2", 0.2, 512));

        assertThat(result.usage().billedCost())
            .as("a $0 call must not be lifted to the minimum billable cost")
            .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("tokens are still recorded — free is not the same as invisible")
    void tokensAreStillRecorded() {
        LLMQueryResult result =
            adapter.queryWithUsage(realisticPrompt(), new LLMConfig("llama3.2", 0.2, 512));

        assertThat(result.usage().totalTokens())
            .as("a free call is still activity, and the ledger should be able to show it")
            .isEqualTo(1500);
        assertThat(result.content()).isEqualTo("BUY");
    }
}
