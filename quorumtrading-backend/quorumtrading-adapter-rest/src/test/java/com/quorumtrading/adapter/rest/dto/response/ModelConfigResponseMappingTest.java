package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.application.usecase.DescribeFallbackBehaviourUseCase;
import com.quorumtrading.application.usecase.GetEffectiveModelsUseCase;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.valueobject.ModelEffort;
import com.quorumtrading.model.valueobject.ModelResolution;
import com.quorumtrading.model.valueobject.ResolutionTier;
import com.quorumtrading.model.valueobject.TradingContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The REST renderings of the two config read models.
 *
 * <p>Both are pure mappings with no computation of their own — which is the point. The frontend
 * previously computed the system default itself, from a hardcoded constant that drifted; a
 * rendering that only renders cannot.</p>
 */
@DisplayName("Config responses render the read models without reinterpreting them")
class ModelConfigResponseMappingTest {

    @Test
    @DisplayName("an effective model renders requested, effective, tier, engine and effort")
    void effectiveModelRendersEveryField() {
        var domain = new GetEffectiveModelsUseCase.EffectiveModel(
                TradingContext.BACKTESTING,
                ModelResolution.substituted("mistralai/mistral", "llama3.2",
                        ResolutionTier.SYSTEM_GLOBAL, LlmProvider.LOCAL_HTTP, "engine does not have it")
                        .withEffort(ModelEffort.HIGH));

        var entry = EffectiveModelsResponse.fromDomain(List.of(domain)).models().get(0);

        assertThat(entry.context()).isEqualTo("BACKTESTING");
        assertThat(entry.requested()).isEqualTo("mistralai/mistral");
        assertThat(entry.effective()).isEqualTo("llama3.2");
        assertThat(entry.source()).isEqualTo("system_default");
        assertThat(entry.decidedByTier()).isEqualTo("SYSTEM_GLOBAL");
        assertThat(entry.engine()).isEqualTo("LOCAL_HTTP");
        assertThat(entry.substitutionReason()).isEqualTo("engine does not have it");
        assertThat(entry.effort()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("an unservable model renders a null effective — a diagnosis, not an omission")
    void unservableRendersNullEffective() {
        var domain = new GetEffectiveModelsUseCase.EffectiveModel(
                TradingContext.LIVE_TRADING,
                ModelResolution.rejected("anthropic/claude-sonnet-4.6", ResolutionTier.USER_GLOBAL,
                        LlmProvider.LOCAL_HTTP, "not advertised"));

        var entry = EffectiveModelsResponse.fromDomain(List.of(domain)).models().get(0);

        assertThat(entry.effective()).isNull();
        assertThat(entry.requested()).isEqualTo("anthropic/claude-sonnet-4.6");
        assertThat(entry.source()).isEqualTo("user_override");
    }

    @Test
    @DisplayName("nothing resolved renders as unresolved with no engine")
    void unresolvedRenders() {
        var domain = new GetEffectiveModelsUseCase.EffectiveModel(
                TradingContext.CHAT, ModelResolution.unresolved(null));

        var entry = EffectiveModelsResponse.fromDomain(List.of(domain)).models().get(0);

        assertThat(entry.source()).isEqualTo("unresolved");
        assertThat(entry.engine()).isNull();
        assertThat(entry.effort()).isNull();
    }

    @Test
    @DisplayName("a strategy pin renders as a strategy override")
    void strategyPinRendersAsStrategyOverride() {
        var domain = new GetEffectiveModelsUseCase.EffectiveModel(
                TradingContext.BACKTESTING,
                ModelResolution.served("sonnet", ResolutionTier.STRATEGY_GLOBAL, LlmProvider.LOCAL));

        assertThat(EffectiveModelsResponse.fromDomain(List.of(domain)).models().get(0).source())
                .isEqualTo("strategy_override");
    }

    @Test
    @DisplayName("the fallback response names the engine and the KIND of chain, not just a list")
    void fallbackRendersKindAndEngine() {
        var behaviour = new DescribeFallbackBehaviourUseCase.FallbackBehaviour(
                LlmProvider.LOCAL, DescribeFallbackBehaviourUseCase.Kind.TOOL_CHAIN,
                List.of("claude"), "tool chain explanation", false);

        FallbackConfigResponse response = FallbackConfigResponse.from(behaviour);

        assertThat(response.engine()).isEqualTo("LOCAL");
        assertThat(response.kind()).isEqualTo("TOOL_CHAIN");
        assertThat(response.fallbackChain()).containsExactly("claude");
        assertThat(response.description()).isEqualTo("tool chain explanation");
        assertThat(response.crossEngineFailover()).isFalse();
    }

    @Test
    @DisplayName("with no engine selected the response says so rather than inventing one")
    void fallbackWithNoEngine() {
        var behaviour = new DescribeFallbackBehaviourUseCase.FallbackBehaviour(
                null, DescribeFallbackBehaviourUseCase.Kind.NONE, List.of(), "nothing selected", false);

        FallbackConfigResponse response = FallbackConfigResponse.from(behaviour);

        assertThat(response.engine()).isNull();
        assertThat(response.kind()).isEqualTo("NONE");
        assertThat(response.fallbackChain()).isEmpty();
    }
}
