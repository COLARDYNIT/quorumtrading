package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.ActiveLlmEnginePort;
import com.quorumtrading.application.port.out.LLMPort;
import com.quorumtrading.application.port.out.SystemModelDefaultsPort;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.service.EngineCapabilityService;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.valueobject.ModelEffort;
import com.quorumtrading.model.valueobject.ModelId;
import com.quorumtrading.model.valueobject.ModelPreferences;
import com.quorumtrading.model.valueobject.ModelResolution;
import com.quorumtrading.model.valueobject.ResolutionTier;
import com.quorumtrading.model.valueobject.TradingContext;
import com.quorumtrading.model.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The one answer to "which model actually runs?", for every trading context.
 *
 * <p>Three surfaces used to answer this question three different ways — {@code get_model_preferences}
 * from config plus the DB, {@code estimate_backtest_cost} from a two-tier resolver of its own, and
 * the settings page from a hardcoded frontend constant that had already drifted (it advertised a
 * model the backend's own comments marked DEPRECATED and sunset). At least one of them was always
 * lying. Per the repo's read-model rule, the derived view belongs in one use case that every driving
 * adapter renders.</p>
 *
 * <p>The value is computed the same way a real call would compute it: user preference tiers, then
 * engine-scoped system defaults, then negotiation against the engine that will actually serve it.
 * So a reported model is one the engine would genuinely run, or an explicit statement that it would
 * not.</p>
 *
 * <p>Plain POJO wired in {@code ApplicationConfig}, per the CDI convention.</p>
 */
public class GetEffectiveModelsUseCase {

    private static final Logger logger = LoggerFactory.getLogger(GetEffectiveModelsUseCase.class);

    private final UserBudgetRepository userBudgetRepository;
    private final SystemModelDefaultsPort systemModelDefaultsPort;
    private final ActiveLlmEnginePort activeLlmEnginePort;
    private final EngineCapabilityService engineCapabilityService;
    private final LLMPort llmPort;

    public GetEffectiveModelsUseCase(UserBudgetRepository userBudgetRepository,
                                     SystemModelDefaultsPort systemModelDefaultsPort,
                                     ActiveLlmEnginePort activeLlmEnginePort,
                                     EngineCapabilityService engineCapabilityService,
                                     LLMPort llmPort) {
        this.userBudgetRepository = userBudgetRepository;
        this.systemModelDefaultsPort = systemModelDefaultsPort;
        this.activeLlmEnginePort = activeLlmEnginePort;
        this.engineCapabilityService = engineCapabilityService;
        this.llmPort = llmPort;
    }

    /**
     * The effective model for every trading context.
     *
     * @param userId whose preferences apply; null yields the deployment's defaults
     */
    public List<EffectiveModel> execute(UserId userId) {
        ModelPreferences preferences = preferencesOf(userId);
        LlmProvider engine = activeEngine();

        List<EffectiveModel> result = new ArrayList<>();
        for (TradingContext context : TradingContext.values()) {
            result.add(effectiveFor(context, preferences, engine));
        }
        return List.copyOf(result);
    }

    /** The effective model for one context. */
    public EffectiveModel effectiveFor(TradingContext context, ModelPreferences preferences, LlmProvider engine) {
        Optional<ModelId> userGlobal = preferences.getFor(context);
        ModelEffort effort = preferences.effortFor(
                com.quorumtrading.model.valueobject.ModelPreferenceKey.global(context)).orElse(null);

        String chosen;
        ResolutionTier tier;
        if (userGlobal.isPresent()) {
            chosen = userGlobal.get().value();
            tier = ResolutionTier.USER_GLOBAL;
        } else {
            chosen = systemModelDefaultsPort.getDefaultModel(engine, context).orElse(null);
            tier = chosen == null ? ResolutionTier.NONE : ResolutionTier.SYSTEM_GLOBAL;
        }

        ModelResolution resolution = negotiate(chosen, tier, engine).withEffort(effort);
        return new EffectiveModel(context, resolution);
    }

    /** The engine's own catalogue size, so a caller can say how many models it could offer. */
    public int catalogueSize() {
        try {
            return llmPort.getAvailableModels().size();
        } catch (RuntimeException e) {
            logger.debug("Could not size the active engine's catalogue: {}", e.getMessage());
            return 0;
        }
    }

    private ModelResolution negotiate(String chosen, ResolutionTier tier, LlmProvider engine) {
        if (chosen == null) {
            return ModelResolution.unresolved(engine);
        }
        if (engineCapabilityService == null) {
            return ModelResolution.served(chosen, tier, engine);
        }
        try {
            return engineCapabilityService.negotiate(chosen, tier);
        } catch (RuntimeException e) {
            logger.debug("Could not negotiate '{}' with the active engine: {}", chosen, e.getMessage());
            return ModelResolution.served(chosen, tier, engine);
        }
    }

    private ModelPreferences preferencesOf(UserId userId) {
        if (userId == null) {
            return ModelPreferences.EMPTY;
        }
        try {
            return userBudgetRepository.findByUserId(userId)
                    .map(UserBudget::getModelPreferences)
                    .orElse(ModelPreferences.EMPTY);
        } catch (RuntimeException e) {
            logger.warn("Could not read model preferences for {}: {}", userId.value(), e.getMessage());
            return ModelPreferences.EMPTY;
        }
    }

    private LlmProvider activeEngine() {
        try {
            return activeLlmEnginePort.activeEngine().orElse(null);
        } catch (RuntimeException e) {
            logger.debug("Could not determine the active LLM engine: {}", e.getMessage());
            return null;
        }
    }

    /**
     * What one trading context will actually run.
     *
     * @param context    the trading context
     * @param resolution requested vs effective, the tier that decided, the engine, and why they differ
     */
    public record EffectiveModel(TradingContext context, ModelResolution resolution) {

        /** The model that will run, or null when nothing servable resolved. */
        public String effective() {
            return resolution.effective();
        }

        /** Where the choice came from, in the vocabulary the existing surfaces already use. */
        public String source() {
            return switch (resolution.decidedByTier()) {
                case USER_AGENT, USER_GLOBAL -> "user_override";
                case STRATEGY_AGENT, STRATEGY_GLOBAL -> "strategy_override";
                case SYSTEM_AGENT, SYSTEM_GLOBAL -> "system_default";
                case NONE -> "unresolved";
            };
        }
    }
}
