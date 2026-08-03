package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.ActiveLlmEnginePort;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.SystemModelDefaultsPort;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.service.EngineCapabilityService;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.valueobject.LLMModelInfo;
import com.quorumtrading.model.valueobject.LLMPricing;
import com.quorumtrading.model.valueobject.ModelEffort;
import com.quorumtrading.model.valueobject.ModelId;
import com.quorumtrading.model.valueobject.ModelListingCapability;
import com.quorumtrading.model.valueobject.ModelPreferenceKey;
import com.quorumtrading.model.valueobject.ModelPreferences;
import com.quorumtrading.model.valueobject.TradingContext;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The one computed answer to "which model actually runs?".
 *
 * <p>Three surfaces used to derive this independently, and the frontend derived it from a constant
 * that had drifted into advertising a retired model. Everything now renders this, so the tests that
 * matter are about it reporting what the ENGINE would do rather than what config says.</p>
 */
@DisplayName("GetEffectiveModelsUseCase — the value every surface renders")
class GetEffectiveModelsUseCaseTest {

    private static final UserId USER = new UserId(UUID.randomUUID());
    private static final String LOCAL_MODEL = "llama3.2";
    private static final String HOSTED_DEFAULT = "mistralai/mistral-small-3.2-24b-instruct";

    private UserBudgetRepository budgets;
    private SystemModelDefaultsPort systemDefaults;
    private LLMPort llmPort;

    @BeforeEach
    void setUp() {
        budgets = mock(UserBudgetRepository.class);
        when(budgets.findByUserId(any())).thenReturn(Optional.empty());

        systemDefaults = mock(SystemModelDefaultsPort.class);
        when(systemDefaults.getDefaultModel(any(), any(TradingContext.class)))
                .thenReturn(Optional.of(HOSTED_DEFAULT));

        llmPort = mock(LLMPort.class);
        when(llmPort.modelListingCapability()).thenReturn(ModelListingCapability.ENUMERABLE);
        when(llmPort.getAvailableModels())
                .thenReturn(List.of(LLMModelInfo.of(LOCAL_MODEL, LOCAL_MODEL, LLMPricing.FREE)));
        when(llmPort.getModelInfo(anyString())).thenAnswer(invocation ->
                LOCAL_MODEL.equals(invocation.getArgument(0))
                        ? Optional.of(LLMModelInfo.of(LOCAL_MODEL, LOCAL_MODEL, LLMPricing.FREE))
                        : Optional.empty());
    }

    private GetEffectiveModelsUseCase useCase() {
        ActiveLlmEnginePort engine = () -> Optional.of(LlmProvider.LOCAL_HTTP);
        return new GetEffectiveModelsUseCase(budgets, systemDefaults, engine,
                new EngineCapabilityService(llmPort, engine), llmPort);
    }

    private void userPrefers(TradingContext context, String model, ModelEffort effort) {
        UserBudget budget = UserBudget.createUnlimited(USER);
        budget.setModelPreference(context, ModelId.of(model));
        ModelPreferences prefs = budget.getModelPreferences();
        if (effort != null) {
            prefs = prefs.withEffort(ModelPreferenceKey.global(context), effort);
        }
        UserBudget withEffort = mock(UserBudget.class);
        when(withEffort.getModelPreferences()).thenReturn(prefs);
        when(budgets.findByUserId(USER)).thenReturn(Optional.of(withEffort));
    }

    @Test
    @DisplayName("every trading context gets an answer")
    void answersForEveryContext() {
        assertThat(useCase().execute(USER))
                .hasSize(TradingContext.values().length)
                .extracting(m -> m.context())
                .containsExactlyInAnyOrder(TradingContext.values());
    }

    @Test
    @DisplayName("a system default the engine cannot serve is reported as SUBSTITUTED, not as itself")
    void substitutionIsVisible() {
        var backtesting = useCase().execute(USER).stream()
                .filter(m -> m.context() == TradingContext.BACKTESTING)
                .findFirst().orElseThrow();

        assertThat(backtesting.resolution().requested()).isEqualTo(HOSTED_DEFAULT);
        assertThat(backtesting.effective())
                .as("the surface must report what RUNS, not what config says")
                .isEqualTo(LOCAL_MODEL);
        assertThat(backtesting.resolution().wasSubstituted()).isTrue();
        assertThat(backtesting.source()).isEqualTo("system_default");
    }

    @Test
    @DisplayName("a user preference the engine can serve is reported as a user override")
    void userPreferenceIsReported() {
        userPrefers(TradingContext.BACKTESTING, LOCAL_MODEL, null);

        var backtesting = useCase().execute(USER).stream()
                .filter(m -> m.context() == TradingContext.BACKTESTING)
                .findFirst().orElseThrow();

        assertThat(backtesting.effective()).isEqualTo(LOCAL_MODEL);
        assertThat(backtesting.source()).isEqualTo("user_override");
    }

    @Test
    @DisplayName("a user preference the engine cannot serve reports NO effective model — a diagnosis, not a crash")
    void unservableUserPreferenceHasNoEffectiveModel() {
        userPrefers(TradingContext.BACKTESTING, "anthropic/claude-sonnet-4.6", null);

        var backtesting = useCase().execute(USER).stream()
                .filter(m -> m.context() == TradingContext.BACKTESTING)
                .findFirst().orElseThrow();

        assertThat(backtesting.effective()).isNull();
        assertThat(backtesting.resolution().requested()).isEqualTo("anthropic/claude-sonnet-4.6");
        assertThat(backtesting.resolution().engine()).isEqualTo(LlmProvider.LOCAL_HTTP);
    }

    @Test
    @DisplayName("the requested effort travels with the answer")
    void effortIsCarried() {
        userPrefers(TradingContext.BACKTESTING, LOCAL_MODEL, ModelEffort.HIGH);

        var backtesting = useCase().execute(USER).stream()
                .filter(m -> m.context() == TradingContext.BACKTESTING)
                .findFirst().orElseThrow();

        assertThat(backtesting.resolution().effort()).isEqualTo(ModelEffort.HIGH);
    }

    @Test
    @DisplayName("nothing configured anywhere reads as unresolved rather than as a default")
    void nothingConfiguredIsUnresolved() {
        when(systemDefaults.getDefaultModel(any(), any(TradingContext.class))).thenReturn(Optional.empty());

        var backtesting = useCase().execute(USER).stream()
                .filter(m -> m.context() == TradingContext.BACKTESTING)
                .findFirst().orElseThrow();

        assertThat(backtesting.effective()).isNull();
        assertThat(backtesting.source()).isEqualTo("unresolved");
    }

    @Test
    @DisplayName("no user still yields the deployment's answer")
    void anonymousStillResolves() {
        assertThat(useCase().execute(null)).hasSize(TradingContext.values().length);
    }

    @Test
    @DisplayName("a repository failure degrades to the system defaults rather than failing the page")
    void repositoryFailureDegrades() {
        when(budgets.findByUserId(USER)).thenThrow(new IllegalStateException("db down"));

        assertThat(useCase().execute(USER)).isNotEmpty();
    }

    @Test
    @DisplayName("the catalogue size is reported, so a caller can say how many models exist")
    void catalogueSizeIsReported() {
        assertThat(useCase().catalogueSize()).isEqualTo(1);

        when(llmPort.getAvailableModels()).thenThrow(new IllegalStateException("unreachable"));
        assertThat(useCase().catalogueSize()).isZero();
    }
}
