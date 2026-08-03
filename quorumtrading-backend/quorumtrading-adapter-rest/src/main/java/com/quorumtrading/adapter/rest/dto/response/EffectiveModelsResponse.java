package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.application.usecase.GetEffectiveModelsUseCase;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * What each trading context will actually run.
 *
 * <p>Pure rendering of {@link GetEffectiveModelsUseCase}'s read model. The frontend previously had
 * no endpoint to ask, so it hardcoded the backend's defaults in a constant — which then drifted,
 * advertising a deprecated and sunset model as the paper-trading default. A rendering with no
 * computation of its own cannot drift.</p>
 */
@Schema(description = "The model each trading context will actually run on the active LLM engine")
public record EffectiveModelsResponse(
    @Schema(description = "One entry per trading context")
    List<EffectiveModelEntry> models
) {

    public static EffectiveModelsResponse fromDomain(List<GetEffectiveModelsUseCase.EffectiveModel> models) {
        return new EffectiveModelsResponse(models.stream().map(EffectiveModelEntry::fromDomain).toList());
    }

    /**
     * @param context            trading context, e.g. BACKTESTING
     * @param requested          the model the resolution chain chose
     * @param effective          the model that will actually run; null when nothing servable resolved,
     *                           which reads as a diagnosis rather than a crash
     * @param source             user_override / system_default / strategy_override / unresolved
     * @param decidedByTier      which tier of the resolution chain decided
     * @param engine             the engine that will serve it, null when none is selected
     * @param substitutionReason why effective differs from requested, or null
     * @param effort             requested reasoning effort, null when none was expressed
     */
    @Schema(description = "Effective model for one trading context")
    public record EffectiveModelEntry(
        @Schema(example = "BACKTESTING") String context,
        @Schema(example = "mistralai/mistral-small-3.2-24b-instruct") String requested,
        @Schema(example = "llama3.2", nullable = true) String effective,
        @Schema(example = "system_default") String source,
        @Schema(example = "SYSTEM_GLOBAL") String decidedByTier,
        @Schema(example = "LOCAL_HTTP", nullable = true) String engine,
        @Schema(nullable = true) String substitutionReason,
        @Schema(example = "HIGH", nullable = true) String effort
    ) {
        static EffectiveModelEntry fromDomain(GetEffectiveModelsUseCase.EffectiveModel model) {
            var resolution = model.resolution();
            return new EffectiveModelEntry(
                model.context().name(),
                resolution.requested(),
                resolution.effective(),
                model.source(),
                resolution.decidedByTier().name(),
                resolution.engine() == null ? null : resolution.engine().name(),
                resolution.substitutionReason(),
                resolution.effort() == null ? null : resolution.effort().name()
            );
        }
    }
}
