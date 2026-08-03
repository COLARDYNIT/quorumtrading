package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.ActiveLlmEnginePort;
import com.quorumtrading.application.port.out.SystemModelDefaultsPort;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.enums.AgentKind;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.valueobject.ModelId;
import com.quorumtrading.model.valueobject.ModelPreferences;
import com.quorumtrading.model.valueobject.ModelResolution;
import com.quorumtrading.model.valueobject.ResearchAgentType;
import com.quorumtrading.model.valueobject.ResolutionTier;
import com.quorumtrading.model.valueobject.TradingContext;
import com.quorumtrading.model.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Research model resolution, on the same chain as consensus.
 *
 * <p>Research used to resolve models entirely on its own: a user's <em>global</em> RESEARCH
 * preference, else a default read from a duplicate config key, applied identically to every research
 * agent. Two consequences followed. Per-agent research preferences were storable and ignored — the
 * user could set one and watch it do nothing. And the shipped
 * {@code quorumtrading.llm.defaults.research.orchestrator} key could never have had an effect,
 * because research has no orchestrator at all.</p>
 *
 * <p>With {@link AgentKind} spanning both vocabularies, research inherits tiers 3–6 unchanged.
 * Tiers 1–2 do not apply and are not faked: they are <em>strategy</em> pins, and research is what
 * produces strategies rather than running under one.</p>
 *
 * <p>Plain POJO wired in {@code ApplicationConfig}, per the CDI convention.</p>
 */
public class ResearchModelResolver {

    private static final Logger logger = LoggerFactory.getLogger(ResearchModelResolver.class);

    private final UserBudgetRepository userBudgetRepository;
    private final SystemModelDefaultsPort systemModelDefaultsPort;
    private final ActiveLlmEnginePort activeLlmEnginePort;
    private final EngineCapabilityService engineCapabilityService;

    public ResearchModelResolver(UserBudgetRepository userBudgetRepository,
                                 SystemModelDefaultsPort systemModelDefaultsPort,
                                 ActiveLlmEnginePort activeLlmEnginePort,
                                 EngineCapabilityService engineCapabilityService) {
        this.userBudgetRepository = userBudgetRepository;
        this.systemModelDefaultsPort = systemModelDefaultsPort;
        this.activeLlmEnginePort = activeLlmEnginePort;
        this.engineCapabilityService = engineCapabilityService;
    }

    /**
     * The model a named research agent should use for this owner.
     *
     * @param ownerId  whose preferences apply; null falls through to the system defaults
     * @param agent    which research agent is about to run
     * @param override an explicit caller-supplied model that wins over everything, or null
     */
    public ModelResolution resolve(UserId ownerId, ResearchAgentType agent, String override) {
        if (override != null && !override.isBlank()) {
            return negotiate(override, ResolutionTier.USER_GLOBAL);
        }

        AgentKind kind = AgentKind.of(agent);
        ModelPreferences preferences = preferencesOf(ownerId);

        // Effort belongs to the user's preference entry, so it applies only when a user tier wins.
        com.quorumtrading.model.valueobject.ModelEffort effort =
                preferences.resolveEffortForAgent(TradingContext.RESEARCH, kind).orElse(null);

        // Tier 3: user per-agent — the tier research never had.
        Optional<ModelId> perAgent = preferences.getForAgent(TradingContext.RESEARCH, kind);
        if (perAgent.isPresent()) {
            logger.debug("{}: using user per-agent research model: {}", agent, perAgent.get().value());
            return negotiate(perAgent.get().value(), ResolutionTier.USER_AGENT).withEffort(effort);
        }

        // Tier 4: user global for RESEARCH.
        Optional<ModelId> global = preferences.getFor(TradingContext.RESEARCH);
        if (global.isPresent()) {
            logger.debug("{}: using user global research model: {}", agent, global.get().value());
            return negotiate(global.get().value(), ResolutionTier.USER_GLOBAL).withEffort(effort);
        }

        LlmProvider engine = activeEngine();

        // Tier 5: system per-agent. Only meaningful for kinds the config actually names; the
        // research vocabulary has no per-agent keys shipped, so this normally misses.
        Optional<String> systemPerAgent = kind.asAgentRole()
                .flatMap(role -> systemModelDefaultsPort.getDefaultModel(TradingContext.RESEARCH, role));
        if (systemPerAgent.isPresent()) {
            return negotiate(systemPerAgent.get(), ResolutionTier.SYSTEM_AGENT);
        }

        // Tier 6: system global for RESEARCH — the one key, read through the one chain.
        Optional<String> systemGlobal =
                systemModelDefaultsPort.getDefaultModel(engine, TradingContext.RESEARCH);
        return systemGlobal
                .map(model -> negotiate(model, ResolutionTier.SYSTEM_GLOBAL))
                .orElseGet(() -> ModelResolution.unresolved(engine));
    }

    private ModelPreferences preferencesOf(UserId ownerId) {
        if (ownerId == null) {
            return ModelPreferences.EMPTY;
        }
        try {
            return userBudgetRepository.findByUserId(ownerId)
                    .map(UserBudget::getModelPreferences)
                    .orElse(ModelPreferences.EMPTY);
        } catch (RuntimeException e) {
            logger.warn("Could not read model preferences for research owner {}: {}",
                    ownerId.value(), e.getMessage());
            return ModelPreferences.EMPTY;
        }
    }

    private ModelResolution negotiate(String model, ResolutionTier tier) {
        if (engineCapabilityService == null) {
            return ModelResolution.served(model, tier, activeEngine());
        }
        return engineCapabilityService.negotiate(model, tier);
    }

    private LlmProvider activeEngine() {
        try {
            return activeLlmEnginePort == null ? null : activeLlmEnginePort.activeEngine().orElse(null);
        } catch (RuntimeException e) {
            logger.debug("Could not determine the active LLM engine for research: {}", e.getMessage());
            return null;
        }
    }
}
