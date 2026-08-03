package com.quorumtrading.architecture;

import com.quorumtrading.adapter.mcp.tool.McpToolBase;
import com.quorumtrading.adapter.rest.exception.ProblemDetails;
import com.quorumtrading.adapter.rest.exception.ProblemDetailsExceptionMapper;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.enums.ModelTier;
import com.quorumtrading.model.exception.UnservableModelPinException;
import com.quorumtrading.model.valueobject.ModelCandidate;
import com.quorumtrading.model.valueobject.TradingContext;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.TextContent;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Holds the two renderings of a rejected model pin in step.
 *
 * <p>Same pattern, same reason as {@code FeatureGatedToolContractTest}: REST and MCP each render
 * the refusal, and either one alone is a bug that only shows up on one surface. The requirement is
 * that an invalid pin comes back with models that would actually work — so a test that checks only
 * the REST extension member would happily pass while an MCP client, an LLM that could self-correct
 * in the same turn, got a bare "unknown model" and had to spend a round-trip discovering them.</p>
 *
 * <p>Both renderings are derived from one exception built in the use case, which is what makes them
 * unable to disagree; this test is what stops that from being quietly undone.</p>
 */
@DisplayName("Unservable model pin — REST and MCP say the same thing")
class UnservableModelPinRenderingContractTest {

    private static final String PINNED = "anthropic/claude-sonnet-4.6";

    private static final List<ModelCandidate> CANDIDATES = List.of(
            ModelCandidate.rated("llama3.2", ModelTier.PROVEN, TradingContext.BACKTESTING, "works locally"),
            ModelCandidate.untested("qwen3:8b"));

    private static UnservableModelPinException error() {
        return new UnservableModelPinException(PINNED, AgentRole.TECHNICAL, LlmProvider.LOCAL_HTTP,
                CANDIDATES, "See the full list with get_available_models.");
    }

    /** Minimal concrete tool, so the shared error handling can be exercised as a tool would. */
    private static final class ProbeTool extends McpToolBase {
        ToolResponse run(Supplier<String> action) {
            return execute(action);
        }
    }

    private static String mcpRendering() {
        ToolResponse response = new ProbeTool().run(() -> {
            throw error();
        });
        return response.content().stream()
                .filter(TextContent.class::isInstance)
                .map(content -> ((TextContent) content).text())
                .findFirst()
                .orElseThrow();
    }

    private static ProblemDetails restRendering() {
        Response response = new ProblemDetailsExceptionMapper().toResponse(error());
        return (ProblemDetails) response.getEntity();
    }

    @Test
    @DisplayName("MCP names every candidate inline — an LLM client must be able to retry in the same turn")
    void mcpRendersCandidatesInline() {
        String rendered = mcpRendering();

        assertThat(rendered).contains(PINNED);
        assertThat(rendered).contains("LOCAL_HTTP");
        for (ModelCandidate candidate : CANDIDATES) {
            assertThat(rendered).contains(candidate.id());
        }
    }

    @Test
    @DisplayName("REST returns 400 with the same readable text in `detail`")
    void restRepeatsTheSameTextInDetail() {
        ProblemDetails problem = restRendering();

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo(error().getMessage());
    }

    @Test
    @DisplayName("REST additionally exposes the candidates as data, so a UI renders choices not prose")
    void restExposesCandidatesAsAnExtensionMember() {
        ProblemDetails problem = restRendering();

        assertThat(problem.getValidModels())
                .extracting(ProblemDetails.ValidModel::getId)
                .containsExactly("llama3.2", "qwen3:8b");
        assertThat(problem.getValidModels().get(0).getTier()).isEqualTo("PROVEN");
        assertThat(problem.getValidModels().get(0).getBestContext()).isEqualTo("BACKTESTING");
        assertThat(problem.getValidModels().get(1).getTier()).isNull();
        assertThat(problem.getFullModelListing()).isEqualTo("See the full list with get_available_models.");
    }

    @Test
    @DisplayName("neither surface can offer a model the other does not — one exception, two renderings")
    void bothSurfacesOfferTheSameModels() {
        String mcp = mcpRendering();
        List<String> restIds = restRendering().getValidModels().stream()
                .map(ProblemDetails.ValidModel::getId)
                .toList();

        assertThat(restIds).isEqualTo(CANDIDATES.stream().map(ModelCandidate::id).toList());
        for (String id : restIds) {
            assertThat(mcp).as("MCP must mention every model REST offers").contains(id);
        }
    }

    @Test
    @DisplayName("with nothing servable, both surfaces say so rather than offering an empty list")
    void emptyCandidatesAreStatedNotImplied() {
        UnservableModelPinException empty =
                new UnservableModelPinException(PINNED, null, LlmProvider.LOCAL_HTTP, List.of(), null);

        assertThat(empty.getMessage()).contains("no models at all");

        ProblemDetails problem = (ProblemDetails) new ProblemDetailsExceptionMapper()
                .toResponse(empty).getEntity();
        assertThat(problem.getValidModels()).isEmpty();
        assertThat(problem.getDetail()).isEqualTo(empty.getMessage());
    }
}
