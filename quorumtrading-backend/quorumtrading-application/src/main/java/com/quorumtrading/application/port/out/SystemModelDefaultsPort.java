package com.quorumtrading.application.port.out;

import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.enums.LlmProvider;
import com.quorumtrading.model.valueobject.TradingContext;

import java.util.Map;
import java.util.Optional;

/**
 * Port for accessing system-level default model configuration — the defaults configured for the
 * deployment, not user overrides.
 *
 * <p><b>Defaults are engine-scoped.</b> "Use this when nothing better is specified" only has an
 * answer once you know which engine will run it: the same flat default set that is correct for
 * OpenRouter hands an OpenRouter-namespaced id to an Ollama server that has never heard of it.
 * Prefer the {@link LlmProvider}-taking overloads and source the engine from
 * {@link ActiveLlmEnginePort}. The engine-less overloads answer for the deployment's configured
 * defaults and remain correct where the engine genuinely is not known.</p>
 *
 * <p>This is a <em>default</em>, not a capability constraint. Whether the resolved model can
 * actually be served is a separate question answered after resolution, whichever tier won.</p>
 */
public interface SystemModelDefaultsPort {

    /**
     * Get the configured default model for a trading context, without regard to engine.
     */
    Optional<String> getDefaultModel(TradingContext context);

    /**
     * Get all configured system defaults across all trading contexts.
     */
    Map<TradingContext, String> getAllDefaults();

    /**
     * Get the configured default model for a trading context and agent role.
     * Returns empty if no agent-specific default is configured.
     */
    default Optional<String> getDefaultModel(TradingContext context, AgentRole role) {
        return Optional.empty();
    }

    /**
     * Get all per-agent defaults for a trading context.
     * Only roles with explicit defaults are included.
     */
    default Map<AgentRole, String> getAgentDefaults(TradingContext context) {
        return Map.of();
    }

    /**
     * The default model for a context <em>on a specific engine</em>.
     *
     * <p>An engine that pins a model deployment-wide makes that pin its default for every context
     * and role — there is nothing else it could serve, and pretending otherwise is what let the
     * platform report one model and run another.</p>
     *
     * @param engine  the engine that will serve the call, or {@code null} when unknown
     * @param context the trading context
     */
    default Optional<String> getDefaultModel(LlmProvider engine, TradingContext context) {
        return getDefaultModel(context);
    }

    /**
     * Per-agent defaults for a context on a specific engine.
     *
     * @param engine  the engine that will serve the call, or {@code null} when unknown
     * @param context the trading context
     */
    default Map<AgentRole, String> getAgentDefaults(LlmProvider engine, TradingContext context) {
        return getAgentDefaults(context);
    }
}
