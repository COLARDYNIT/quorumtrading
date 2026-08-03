package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.exception.UnservableModelPinException;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.LLMPricing;
import com.quorumtrading.model.valueobject.ModelListingCapability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Write-time refusal of a pin the active engine cannot serve.
 *
 * <p>Fail-hard at run time is only defensible if the unservable pin could never have been stored.
 * Two holes made that untrue: per-agent {@code llm_models} pins — tier 1, the highest precedence —
 * were never validated at all, and a temporarily unreachable engine reported "no listing support",
 * which silently disabled validation entirely.</p>
 */
@DisplayName("ModelPinValidator — an unservable pin cannot be saved, and the refusal is actionable")
class ModelPinValidatorTest {

    private static final String SERVABLE = "llama3.2";
    private static final String UNSERVABLE = "anthropic/claude-sonnet-4.6";

    private static ModelPinValidator validator(ModelListingCapability capability, List<String> catalogue) {
        LLMPort llmPort = mock(LLMPort.class);
        when(llmPort.modelListingCapability()).thenReturn(capability);
        when(llmPort.getAvailableModels())
                .thenReturn(catalogue.stream().map(id -> LLMModelInfo.of(id, id, LLMPricing.FREE)).toList());
        when(llmPort.getModelInfo(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return catalogue.contains(id)
                    ? Optional.of(LLMModelInfo.of(id, id, LLMPricing.FREE))
                    : Optional.empty();
        });
        return new ModelPinValidator(llmPort, () -> Optional.of(LlmProvider.LOCAL_HTTP));
    }

    private static ModelPinValidator healthyEngine() {
        return validator(ModelListingCapability.ENUMERABLE, List.of(SERVABLE, "qwen3:8b"));
    }

    private static StrategyConfig strategy(String globalPin, Map<AgentRole, String> agentPins) {
        StrategyConfig config = new StrategyConfig();
        config.setLlmModel(globalPin);
        config.setLlmAgentModels(agentPins);
        return config;
    }

    @Test
    @DisplayName("a strategy pinning a servable model is accepted")
    void servableGlobalPinIsAccepted() {
        assertThatCode(() -> healthyEngine().validateStrategyPins(strategy(SERVABLE, Map.of())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a strategy with no pins at all is accepted")
    void noPinsIsAccepted() {
        assertThatCode(() -> healthyEngine().validateStrategyPins(strategy(null, Map.of())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an unservable llm_model is refused, naming the model, the engine and what would work")
    void unservableGlobalPinIsRefused() {
        assertThatThrownBy(() -> healthyEngine().validateStrategyPins(strategy(UNSERVABLE, Map.of())))
                .isInstanceOf(UnservableModelPinException.class)
                .hasMessageContaining(UNSERVABLE)
                .hasMessageContaining("LOCAL_HTTP")
                .hasMessageContaining(SERVABLE);
    }

    @Test
    @DisplayName("an unservable per-agent llm_models pin is refused too — it is the HIGHER precedence one")
    void unservablePerAgentPinIsRefused() {
        StrategyConfig config = strategy(SERVABLE, Map.of(AgentRole.TECHNICAL, UNSERVABLE));

        assertThatThrownBy(() -> healthyEngine().validateStrategyPins(config))
                .isInstanceOf(UnservableModelPinException.class)
                .hasMessageContaining(UNSERVABLE)
                .hasMessageContaining("TECHNICAL");
    }

    @Test
    @DisplayName("the refusal carries the candidates as data, not only as prose")
    void refusalCarriesCandidatesAsData() {
        UnservableModelPinException error = healthyEngine().unservable(UNSERVABLE, AgentRole.SENTIMENT);

        assertThat(error.getPinnedId()).isEqualTo(UNSERVABLE);
        assertThat(error.getAgentRole()).isEqualTo(AgentRole.SENTIMENT);
        assertThat(error.getEngine()).isEqualTo(LlmProvider.LOCAL_HTTP);
        assertThat(error.getCandidates()).extracting(c -> c.id()).containsExactly(SERVABLE, "qwen3:8b");
    }

    @Test
    @DisplayName("no 'see the full list' hint when the candidates ARE the full list")
    void noFullListingHintForASmallCatalogue() {
        assertThat(healthyEngine().unservable(UNSERVABLE, null).getFullListingHint()).isNull();
    }

    @Test
    @DisplayName("an engine with no catalogue by design accepts any pin — nothing can be checked")
    void nonEnumerableEngineAcceptsAnything() {
        ModelPinValidator validator = validator(ModelListingCapability.NOT_SUPPORTED, List.of());

        assertThatCode(() -> validator.validateStrategyPins(strategy(UNSERVABLE, Map.of())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an unreachable engine does not reject — it cannot know, and guessing either way is wrong")
    void unavailableEngineDoesNotReject() {
        ModelPinValidator validator = validator(ModelListingCapability.UNAVAILABLE, List.of());

        assertThatCode(() -> validator.validateStrategyPins(strategy(UNSERVABLE, Map.of())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("isServable distinguishes the three engine states rather than collapsing them to a boolean")
    void isServableReflectsEngineState() {
        assertThat(healthyEngine().isServable(SERVABLE)).isTrue();
        assertThat(healthyEngine().isServable(UNSERVABLE)).isFalse();
        assertThat(validator(ModelListingCapability.NOT_SUPPORTED, List.of()).isServable(UNSERVABLE)).isTrue();
        assertThat(validator(ModelListingCapability.UNAVAILABLE, List.of()).isServable(UNSERVABLE)).isTrue();
        assertThat(healthyEngine().isServable(null)).isTrue();
    }
}
