package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.ActiveLlmEnginePort;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.LLMPricing;
import com.quorumtrading.model.valueobject.ModelListingCapability;
import com.quorumtrading.model.valueobject.ModelResolution;
import com.quorumtrading.model.valueobject.ResolutionTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The reject-vs-substitute policy, which is the whole point of the exercise.
 *
 * <p>An explicit choice that cannot be served is an error. A system default that cannot be served
 * is just a default that missed, and the engine's own model is the sensible answer. Getting this
 * backwards is how a backtest came to report a model it never ran.</p>
 */
@DisplayName("EngineCapabilityService — SERVE / SUBSTITUTE / REJECT")
class EngineCapabilityServiceTest {

    private static final String LOCAL_MODEL = "llama3.2";
    private static final String FOREIGN_MODEL = "anthropic/claude-sonnet-4.6";

    private static LLMModelInfo model(String id) {
        return LLMModelInfo.of(id, id, LLMPricing.FREE);
    }

    private static EngineCapabilityService service(ModelListingCapability capability, List<String> catalogue) {
        LLMPort llmPort = mock(LLMPort.class);
        when(llmPort.modelListingCapability()).thenReturn(capability);
        when(llmPort.getAvailableModels()).thenReturn(catalogue.stream().map(EngineCapabilityServiceTest::model).toList());
        when(llmPort.getModelInfo(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return catalogue.contains(id) ? Optional.of(model(id)) : Optional.empty();
        });
        ActiveLlmEnginePort engine = () -> Optional.of(LlmProvider.LOCAL_HTTP);
        return new EngineCapabilityService(llmPort, engine);
    }

    @Test
    @DisplayName("a model the engine advertises is served unchanged, whatever tier chose it")
    void servableModelIsServed() {
        EngineCapabilityService service = service(ModelListingCapability.ENUMERABLE, List.of(LOCAL_MODEL));

        ModelResolution resolution = service.negotiate(LOCAL_MODEL, ResolutionTier.STRATEGY_GLOBAL);

        assertThat(resolution.isServable()).isTrue();
        assertThat(resolution.effective()).isEqualTo(LOCAL_MODEL);
        assertThat(resolution.wasSubstituted()).isFalse();
        assertThat(resolution.engine()).isEqualTo(LlmProvider.LOCAL_HTTP);
    }

    @ParameterizedTest
    @EnumSource(value = ResolutionTier.class,
            names = {"STRATEGY_AGENT", "STRATEGY_GLOBAL", "USER_AGENT", "USER_GLOBAL"})
    @DisplayName("an explicit choice the engine cannot serve is REJECTED, never swapped")
    void explicitChoicesAreRejected(ResolutionTier tier) {
        EngineCapabilityService service = service(ModelListingCapability.ENUMERABLE, List.of(LOCAL_MODEL));

        ModelResolution resolution = service.negotiate(FOREIGN_MODEL, tier);

        assertThat(resolution.isServable()).isFalse();
        assertThat(resolution.effective()).isNull();
        assertThat(resolution.requested()).isEqualTo(FOREIGN_MODEL);
        assertThat(resolution.decidedByTier()).isEqualTo(tier);
        assertThat(resolution.substitutionReason()).contains("does not advertise");
    }

    @ParameterizedTest
    @EnumSource(value = ResolutionTier.class, names = {"SYSTEM_AGENT", "SYSTEM_GLOBAL"})
    @DisplayName("a system default the engine cannot serve is SUBSTITUTED, and the swap is queryable")
    void systemDefaultsSubstitute(ResolutionTier tier) {
        EngineCapabilityService service = service(ModelListingCapability.ENUMERABLE, List.of(LOCAL_MODEL));

        ModelResolution resolution = service.negotiate(FOREIGN_MODEL, tier);

        assertThat(resolution.isServable()).isTrue();
        assertThat(resolution.effective()).isEqualTo(LOCAL_MODEL);
        assertThat(resolution.requested()).isEqualTo(FOREIGN_MODEL);
        assertThat(resolution.wasSubstituted()).isTrue();
        assertThat(resolution.substitutionReason()).isNotBlank();
    }

    @Test
    @DisplayName("a system default survives an empty catalogue — refusing would block a run nobody asked for")
    void systemDefaultSurvivesAnEmptyCatalogue() {
        // This asserted rejection, and that was the bug: an engine reporting an empty catalogue has
        // told us nothing, and refusing on that basis blocked every consensus call in the keyless
        // e2e profile. Tiers 5-6 must never refuse — the model here is the platform's own default,
        // which nobody chose.
        EngineCapabilityService service = service(ModelListingCapability.ENUMERABLE, List.of());

        ModelResolution resolution = service.negotiate(FOREIGN_MODEL, ResolutionTier.SYSTEM_GLOBAL);

        assertThat(resolution.isServable()).isTrue();
        assertThat(resolution.effective()).isEqualTo(FOREIGN_MODEL);
    }

    @Test
    @DisplayName("an explicit choice IS still refused when the engine advertises nothing")
    void explicitChoiceStillRefusedWithAnEmptyCatalogue() {
        // The leniency above is scoped to defaults. Someone who named a model still gets told.
        EngineCapabilityService service = service(ModelListingCapability.ENUMERABLE, List.of());

        assertThat(service.negotiate(FOREIGN_MODEL, ResolutionTier.STRATEGY_GLOBAL).isServable())
                .isFalse();
    }

    @Test
    @DisplayName("an engine with no catalogue by design is trusted — there is nothing to check against")
    void nonEnumerableEngineIsTrusted() {
        EngineCapabilityService service = service(ModelListingCapability.NOT_SUPPORTED, List.of());

        ModelResolution resolution = service.negotiate(FOREIGN_MODEL, ResolutionTier.STRATEGY_GLOBAL);

        assertThat(resolution.isServable()).isTrue();
        assertThat(resolution.effective()).isEqualTo(FOREIGN_MODEL);
    }

    @Test
    @DisplayName("an unreachable catalogue does not refuse a call the engine might well serve")
    void unavailableCatalogueDoesNotReject() {
        EngineCapabilityService service = service(ModelListingCapability.UNAVAILABLE, List.of());

        ModelResolution resolution = service.negotiate(FOREIGN_MODEL, ResolutionTier.USER_GLOBAL);

        assertThat(resolution.isServable()).isTrue();
        assertThat(resolution.effective()).isEqualTo(FOREIGN_MODEL);
    }

    @Test
    @DisplayName("nothing resolved at any tier stays unresolved rather than becoming a guess")
    void nothingResolvedStaysUnresolved() {
        EngineCapabilityService service = service(ModelListingCapability.ENUMERABLE, List.of(LOCAL_MODEL));

        ModelResolution resolution = service.negotiate(null, ResolutionTier.NONE);

        assertThat(resolution.isServable()).isFalse();
        assertThat(resolution.requested()).isNull();
        assertThat(resolution.decidedByTier()).isEqualTo(ResolutionTier.NONE);
    }

    @Test
    @DisplayName("an engine lookup that throws leaves the engine unknown, never guessed")
    void engineLookupFailureLeavesEngineUnknown() {
        LLMPort llmPort = mock(LLMPort.class);
        when(llmPort.modelListingCapability()).thenReturn(ModelListingCapability.NOT_SUPPORTED);
        EngineCapabilityService service = new EngineCapabilityService(llmPort, () -> {
            throw new IllegalStateException("no transaction");
        });

        ModelResolution resolution = service.negotiate(LOCAL_MODEL, ResolutionTier.SYSTEM_GLOBAL);

        assertThat(resolution.engine()).isNull();
        assertThat(resolution.effective()).isEqualTo(LOCAL_MODEL);
    }

    @Test
    @DisplayName("a catalogue read that throws while substituting still lets a system default through")
    void catalogueReadFailureWhileSubstitutingStillServes() {
        LLMPort llmPort = mock(LLMPort.class);
        when(llmPort.modelListingCapability()).thenReturn(ModelListingCapability.ENUMERABLE);
        when(llmPort.getModelInfo(anyString())).thenReturn(Optional.empty());
        when(llmPort.getAvailableModels()).thenThrow(new IllegalStateException("connection closed"));
        EngineCapabilityService service =
                new EngineCapabilityService(llmPort, () -> Optional.of(LlmProvider.LOCAL_HTTP));

        ModelResolution resolution = service.negotiate(FOREIGN_MODEL, ResolutionTier.SYSTEM_GLOBAL);

        // A catalogue that throws tells us even less than an empty one. Refusing the platform's own
        // default on that basis blocks a run for a reason nobody chose.
        assertThat(resolution.isServable()).isTrue();
        assertThat(resolution.effective()).isEqualTo(FOREIGN_MODEL);
    }

    @Test
    @DisplayName("a catalogue that throws is 'could not check', not 'refused'")
    void catalogueFailureProceedsUnverified() {
        LLMPort llmPort = mock(LLMPort.class);
        when(llmPort.modelListingCapability()).thenThrow(new IllegalStateException("connection closed"));
        EngineCapabilityService service =
                new EngineCapabilityService(llmPort, () -> Optional.of(LlmProvider.OPENROUTER));

        ModelResolution resolution = service.negotiate(FOREIGN_MODEL, ResolutionTier.STRATEGY_GLOBAL);

        assertThat(resolution.isServable()).isTrue();
        assertThat(resolution.effective()).isEqualTo(FOREIGN_MODEL);
    }
}
