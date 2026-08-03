package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.application.usecase.DescribeFallbackBehaviourUseCase;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * What "fallback" means on the engine that is actually selected.
 *
 * <p>This used to serve one engine's model chain verbatim from config, regardless of which engine
 * was running — so a deployment on a local server was shown four OpenRouter models, not one of
 * which it could serve. The chain is now derived from the active engine, and its <em>kind</em> is
 * stated, because a cross-model chain and a cross-tool chain are not the same promise.</p>
 */
@Schema(description = "Fallback behaviour of the currently selected LLM engine")
public record FallbackConfigResponse(
    @Schema(description = "The selected engine, or null when none has been chosen", example = "LOCAL_HTTP")
    String engine,

    @Schema(description = "What kind of fallback this engine has: MODEL_CHAIN, TOOL_CHAIN, SERVER_DEFAULT or NONE",
            example = "SERVER_DEFAULT")
    String kind,

    @Schema(description = "The chain in order — models for OpenRouter, INSTALLED tools for the CLI engine, "
            + "empty when the engine has no chain.",
            example = "[\"mistralai/mistral-small-3.2-24b-instruct\", \"meta-llama/llama-4-scout\"]")
    List<String> fallbackChain,

    @Schema(description = "One sentence a user can act on")
    String description,

    @Schema(description = "Whether the platform would fall through to a DIFFERENT engine. False with runtime "
            + "selection: the router returns exactly one provider by design.",
            example = "false")
    boolean crossEngineFailover
) {
    public static FallbackConfigResponse from(DescribeFallbackBehaviourUseCase.FallbackBehaviour behaviour) {
        return new FallbackConfigResponse(
            behaviour.engine() == null ? null : behaviour.engine().name(),
            behaviour.kind().name(),
            behaviour.entries(),
            behaviour.explanation(),
            behaviour.crossEngineFailover()
        );
    }
}
