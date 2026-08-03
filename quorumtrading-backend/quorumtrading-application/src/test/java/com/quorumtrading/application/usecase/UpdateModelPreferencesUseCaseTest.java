package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.ActiveLlmEnginePort;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.service.ModelPinValidator;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.enums.AgentKind;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.exception.UnservableModelPinException;
import com.quorumtrading.model.service.ModelFilter;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.LLMPricing;
import com.quorumtrading.model.valueobject.ModelListingCapability;
import com.quorumtrading.model.valueobject.ModelPreferences;
import com.quorumtrading.model.valueobject.TradingContext;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Storing a model preference, and refusing to store one the active engine will not serve.
 *
 * <p>Fail-hard at run time is only defensible if this write path is airtight, so the interesting
 * cases are the ones that used to slip through: an unreachable engine that silently disabled
 * validation, and an unrecognised key that was logged and dropped while the caller was told the
 * update succeeded.</p>
 */
@DisplayName("UpdateModelPreferencesUseCase — what may be stored")
class UpdateModelPreferencesUseCaseTest {

    private static final UserId USER = new UserId(UUID.randomUUID());
    private static final String SERVABLE = "llama3.2";
    private static final String UNSERVABLE = "anthropic/claude-sonnet-4.6";

    private UserBudgetRepository budgets;
    private LLMPort llmPort;

    @BeforeEach
    void setUp() {
        budgets = mock(UserBudgetRepository.class);
        when(budgets.findByUserId(USER)).thenReturn(Optional.of(UserBudget.createUnlimited(USER)));
        when(budgets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        llmPort = mock(LLMPort.class);
        when(llmPort.modelListingCapability()).thenReturn(ModelListingCapability.ENUMERABLE);
        when(llmPort.getAvailableModels())
                .thenReturn(List.of(LLMModelInfo.of(SERVABLE, SERVABLE, LLMPricing.FREE)));
        when(llmPort.getModelInfo(anyString())).thenAnswer(invocation ->
                SERVABLE.equals(invocation.getArgument(0))
                        ? Optional.of(LLMModelInfo.of(SERVABLE, SERVABLE, LLMPricing.FREE))
                        : Optional.empty());
    }

    private UpdateModelPreferencesUseCase useCase() {
        ActiveLlmEnginePort engine = () -> Optional.of(LlmProvider.LOCAL_HTTP);
        return new UpdateModelPreferencesUseCase(budgets, llmPort, new ModelFilter(),
                new ModelPinValidator(llmPort, engine));
    }

    @Test
    @DisplayName("a servable global preference is stored")
    void servableGlobalPreferenceIsStored() {
        ModelPreferences result = useCase().execute(USER, Map.of(TradingContext.BACKTESTING, SERVABLE));

        assertThat(result.getFor(TradingContext.BACKTESTING))
                .map(m -> m.value()).contains(SERVABLE);
    }

    @Test
    @DisplayName("an unservable preference is refused, with models that would work")
    void unservablePreferenceIsRefused() {
        assertThatThrownBy(() -> useCase().execute(USER, Map.of(TradingContext.BACKTESTING, UNSERVABLE)))
                .isInstanceOf(UnservableModelPinException.class)
                .hasMessageContaining(UNSERVABLE)
                .hasMessageContaining(SERVABLE);

        verify(budgets, never()).save(any());
    }

    @Test
    @DisplayName("a blank value clears the preference rather than storing an empty string")
    void blankClearsThePreference() {
        ModelPreferences result = useCase().execute(USER, TradingContext.BACKTESTING, null);

        assertThat(result.getFor(TradingContext.BACKTESTING)).isEmpty();
    }

    @Test
    @DisplayName("a per-agent override is stored under its own key, including for research agents")
    void perAgentOverrideIsStored() {
        ModelPreferences result = useCase().execute(USER, Map.of(),
                Map.of("RESEARCH_STRATEGY_DESIGNER", SERVABLE));

        assertThat(result.getForAgent(TradingContext.RESEARCH, AgentKind.STRATEGY_DESIGNER))
                .map(m -> m.value()).contains(SERVABLE);
    }

    @Test
    @DisplayName("an unrecognised override key is REFUSED — it used to be dropped in silence")
    void unrecognisedKeyIsRefused() {
        assertThatThrownBy(() -> useCase().execute(USER, Map.of(), Map.of("NONSENSE_KEY", SERVABLE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NONSENSE_KEY");
    }

    @Test
    @DisplayName("a context-global key passed as an agent override is refused rather than half-applied")
    void globalKeyInTheAgentMapIsRefused() {
        assertThatThrownBy(() -> useCase().execute(USER, Map.of(), Map.of("BACKTESTING", SERVABLE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BACKTESTING");
    }

    @Test
    @DisplayName("a blank per-agent value clears that override")
    void blankPerAgentValueClears() {
        ModelPreferences result = useCase().execute(USER, Map.of(),
                Map.of("BACKTESTING_TECHNICAL", ""));

        assertThat(result.getForAgent(TradingContext.BACKTESTING, AgentKind.TECHNICAL)).isEmpty();
    }

    @Test
    @DisplayName("an engine with no catalogue by design accepts anything — nothing can be checked")
    void nonEnumerableEngineSkipsValidation() {
        when(llmPort.modelListingCapability()).thenReturn(ModelListingCapability.NOT_SUPPORTED);

        assertThatCode(() -> useCase().execute(USER, Map.of(TradingContext.BACKTESTING, UNSERVABLE)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an unreachable engine stores unvalidated rather than pretending it checked")
    void unreachableEngineStoresUnvalidated() {
        when(llmPort.modelListingCapability()).thenReturn(ModelListingCapability.UNAVAILABLE);

        assertThatCode(() -> useCase().execute(USER, Map.of(TradingContext.BACKTESTING, UNSERVABLE)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("reading preferences for an unknown user yields empty, not a failure")
    void unknownUserHasEmptyPreferences() {
        when(budgets.findByUserId(USER)).thenReturn(Optional.empty());

        assertThat(useCase().getPreferences(USER)).isEqualTo(ModelPreferences.EMPTY);
    }
}
