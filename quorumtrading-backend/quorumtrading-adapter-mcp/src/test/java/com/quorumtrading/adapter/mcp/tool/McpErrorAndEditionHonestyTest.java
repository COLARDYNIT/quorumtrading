package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.exception.EntityNotFoundException;
import io.quarkiverse.mcp.server.ToolResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A wrong id is the user's mistake; an "Internal error" tells them it is the server's.
 *
 * <p>{@code EntityNotFoundException} and {@code AccessDeniedException} both extend
 * {@code RuntimeException}, so they fell past every specific branch in {@link McpToolBase#execute}
 * into the catch-all and came back as {@code "Internal error: Broker account not found:
 * 00000000-…"}. A newcomer reads that as a backend outage and goes looking for one — while the REST
 * layer, on the same domain call, correctly returns an RFC7807 not-found.</p>
 */
class McpErrorAndEditionHonestyTest {

    /** Minimal concrete tool: {@link McpToolBase#execute} is what is under test, not any one tool. */
    private static final class ProbeTool extends McpToolBase {
        ToolResponse run(Runnable body) {
            return execute(() -> {
                body.run();
                return "{}";
            });
        }
    }

    private final ProbeTool tool = new ProbeTool();

    @Test
    @DisplayName("a not-found is reported as a not-found, not as an internal error")
    void notFoundIsNotAnInternalError() {
        ToolResponse response = tool.run(() -> {
            throw new EntityNotFoundException("Broker account not found: 00000000-0000-0000-0000-000000000001");
        });

        assertThat(response.isError()).isTrue();
        String text = response.firstContent().asText().text();
        assertThat(text).contains("Broker account not found");
        assertThat(text)
            .as("the id was wrong; nothing about the server was")
            .doesNotContain("Internal error");
    }

    @Test
    @DisplayName("an access denial is a deliberate decision, not a crash")
    void accessDeniedIsNotAnInternalError() {
        ToolResponse response = tool.run(() -> {
            throw new AccessDeniedException(null, "Benchmark", "read");
        });

        assertThat(response.isError()).isTrue();
        assertThat(response.firstContent().asText().text()).doesNotContain("Internal error");
    }

    @Test
    @DisplayName("a genuinely unexpected fault is still labelled an internal error")
    void unexpectedFaultsStillSayInternalError() {
        // The point is to stop mislabelling domain outcomes — not to stop reporting real faults.
        ToolResponse response = tool.run(() -> {
            throw new RuntimeException("connection reset by peer");
        });

        assertThat(response.firstContent().asText().text()).contains("Internal error");
    }
}
