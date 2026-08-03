package com.quorumtrading.application.service;

import com.quorumtrading.application.orchestration.agents.LLMQueryException;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.orchestration.AgentContext;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.ModelResolution;
import com.quorumtrading.model.valueobject.ResolutionTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralizes the 6-tier LLM model resolution chain for all agents.
 * <p>
 * Resolution order:
 * <ol>
 *   <li>Strategy agent-specific (llm_models.TECHNICAL in YAML)</li>
 *   <li>Strategy global (llm_model in YAML)</li>
 *   <li>User agent-specific (BACKTESTING_TECHNICAL in user preferences)</li>
 *   <li>User global (BACKTESTING in user preferences)</li>
 *   <li>System agent-specific (application.properties per-agent default)</li>
 *   <li>System global (application.properties context default)</li>
 * </ol>
 */
public class ModelResolutionService {

    private static final Logger logger = LoggerFactory.getLogger(ModelResolutionService.class);

    /**
     * Post-resolution engine negotiation. Optional: when absent the chain answers on its own, which
     * is what unit tests and any deployment without a discoverable engine want.
     */
    private final EngineCapabilityService engineCapabilityService;

    public ModelResolutionService() {
        this(null);
    }

    public ModelResolutionService(EngineCapabilityService engineCapabilityService) {
        this.engineCapabilityService = engineCapabilityService;
    }

    /**
     * The full answer: what the chain chose, what the engine will actually run, and why they differ.
     *
     * <p>Negotiation happens exactly once, here, rather than in each agent — four copies of a policy
     * is four chances for one of them to drift into substituting where it should refuse.</p>
     */
    public ModelResolution resolveEffective(AgentContext context, AgentRole role) {
        TierResolution chosen = resolve(context, role);
        if (engineCapabilityService == null) {
            return chosen.model() == null
                    ? ModelResolution.unresolved(null)
                    : ModelResolution.served(chosen.model(), chosen.tier(), null);
        }
        return engineCapabilityService.negotiate(chosen.model(), chosen.tier());
    }

    /**
     * The negotiated resolution, refusing to proceed when the active engine will not serve it.
     *
     * <p>Failing here is the point. Silently running a different model than the one a strategy
     * pinned produces a backtest result that answers a question nobody asked, and the run looks
     * successful — the worst possible combination. The message names the model, the tier that chose
     * it and the engine that refused, which is everything needed to fix it.</p>
     */
    public ModelResolution requireServableModel(AgentContext context, AgentRole role) {
        ModelResolution resolution = resolveEffective(context, role);
        if (resolution.isServable()) {
            return resolution;
        }
        throw new LLMQueryException(String.format(
                "%s agent cannot run: model '%s' (chosen at tier %s) is not servable by the active "
                + "LLM engine %s — %s. Change the pin or preference, or select an engine that has it.",
                role, resolution.requested(), resolution.decidedByTier(),
                resolution.engine() == null ? "(not selected)" : resolution.engine(),
                resolution.substitutionReason()));
    }

    /**
     * Resolve the model to use for a given agent role.
     *
     * @param context the agent context containing all model preference data
     * @param role    the agent role to resolve for
     * @return the resolved model ID (never null if system defaults are configured)
     */
    public String resolveModel(AgentContext context, AgentRole role) {
        return resolve(context, role).model();
    }

    /**
     * Resolve the model <em>and</em> which tier supplied it.
     *
     * <p>The tier is not decoration. It decides what happens when the chosen model turns out to be
     * unservable — an explicit choice is refused, a default is substituted — so it has to travel
     * with the answer rather than be inferred later.</p>
     */
    public TierResolution resolve(AgentContext context, AgentRole role) {
        // Tier 1: Strategy agent-specific
        StrategyConfig strategy = context.getStrategy();
        if (strategy != null && strategy.getLlmAgentModels() != null) {
            String model = strategy.getLlmAgentModels().get(role);
            if (isPresent(model)) {
                logger.debug("{}: using strategy agent-specific model: {}", role, model);
                return new TierResolution(model, ResolutionTier.STRATEGY_AGENT);
            }
        }

        // Tier 2: Strategy global
        if (strategy != null) {
            String model = strategy.getLlmModel();
            if (isPresent(model)) {
                logger.debug("{}: using strategy global model: {}", role, model);
                return new TierResolution(model, ResolutionTier.STRATEGY_GLOBAL);
            }
        }

        // Tier 3: User agent-specific
        String userAgentModel = context.getPreferredAgentModels().get(role);
        if (isPresent(userAgentModel)) {
            logger.debug("{}: using user agent-specific model: {}", role, userAgentModel);
            return new TierResolution(userAgentModel, ResolutionTier.USER_AGENT);
        }

        // Tier 4: User global
        String userGlobal = context.getPreferredModelGlobal();
        if (isPresent(userGlobal)) {
            logger.debug("{}: using user global model: {}", role, userGlobal);
            return new TierResolution(userGlobal, ResolutionTier.USER_GLOBAL);
        }

        // Tier 5: System agent-specific
        String systemAgent = context.getSystemAgentDefaults().get(role);
        if (isPresent(systemAgent)) {
            logger.debug("{}: using system agent-specific default: {}", role, systemAgent);
            return new TierResolution(systemAgent, ResolutionTier.SYSTEM_AGENT);
        }

        // Tier 6: System global
        String systemGlobal = context.getSystemDefaultModel();
        if (isPresent(systemGlobal)) {
            logger.debug("{}: using system global default: {}", role, systemGlobal);
            return new TierResolution(systemGlobal, ResolutionTier.SYSTEM_GLOBAL);
        }

        // Should never happen if system defaults are configured properly
        logger.warn("{}: no model resolved at any tier, this should not happen", role);
        return new TierResolution(null, ResolutionTier.NONE);
    }

    /**
     * A model and the tier that supplied it, before any engine negotiation.
     *
     * @param model the chosen model id, {@code null} when nothing was configured
     * @param tier  which tier supplied it
     */
    public record TierResolution(String model, ResolutionTier tier) {}

    /**
     * Determine if fallback should be disabled for this agent.
     * Fallback is disabled when a strategy override is set (either agent-specific or global),
     * because the user explicitly chose a model and doesn't want the fallback chain to kick in.
     */
    public boolean shouldDisableFallback(AgentContext context, AgentRole role) {
        StrategyConfig strategy = context.getStrategy();
        if (strategy == null) {
            return false;
        }

        // Check agent-specific strategy override
        if (strategy.getLlmAgentModels() != null) {
            String model = strategy.getLlmAgentModels().get(role);
            if (isPresent(model)) {
                return true;
            }
        }

        // Check global strategy override
        return isPresent(strategy.getLlmModel());
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
