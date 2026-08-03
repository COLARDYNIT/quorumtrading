package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.AgentKind;
import com.quorumtrading.model.enums.AgentRole;

import java.util.Objects;
import java.util.Optional;

/**
 * Composite key for model preferences: TradingContext + optional {@link AgentKind}.
 * Supports flat string serialization for JSONB storage:
 * <ul>
 *   <li>"BACKTESTING" — global preference for a context</li>
 *   <li>"BACKTESTING_TECHNICAL" — per-agent preference (consensus)</li>
 *   <li>"RESEARCH_STRATEGY_DESIGNER" — per-agent preference (research)</li>
 * </ul>
 *
 * <p>The agent half is {@link AgentKind} rather than {@link AgentRole} so trading and research share
 * one storage-key grammar and one resolution chain. {@code AgentKind}'s constants are named exactly
 * as the {@code AgentRole} ones, so keys written before that change still parse.</p>
 */
public record ModelPreferenceKey(TradingContext context, AgentKind agentKind) {

    public ModelPreferenceKey {
        Objects.requireNonNull(context, "context must not be null");
        // agentKind may be null (global key)
    }

    /**
     * Create a global key (no agent).
     */
    public static ModelPreferenceKey global(TradingContext context) {
        return new ModelPreferenceKey(context, null);
    }

    /**
     * Create a per-agent key.
     */
    public static ModelPreferenceKey forAgent(TradingContext context, AgentKind kind) {
        Objects.requireNonNull(kind, "agent kind must not be null for agent-specific key");
        return new ModelPreferenceKey(context, kind);
    }

    /**
     * Create a per-agent key for a consensus agent role.
     */
    public static ModelPreferenceKey forAgent(TradingContext context, AgentRole role) {
        Objects.requireNonNull(role, "role must not be null for agent-specific key");
        return new ModelPreferenceKey(context, AgentKind.of(role));
    }

    /**
     * Returns true if this is a global (context-only) key.
     */
    public boolean isGlobal() {
        return agentKind == null;
    }

    /** The consensus role this key targets, empty for a global or research key. */
    public Optional<AgentRole> agentRole() {
        return agentKind == null ? Optional.empty() : agentKind.asAgentRole();
    }

    /**
     * Serialize to storage key: "BACKTESTING" or "BACKTESTING_TECHNICAL".
     */
    public String toStorageKey() {
        if (agentKind == null) {
            return context.name();
        }
        return context.name() + "_" + agentKind.name();
    }

    /**
     * Parse a storage key back to a ModelPreferenceKey.
     *
     * <p><b>Throws rather than returning null.</b> Returning null meant an unrecognised key was
     * dropped in silence on both the write path and the read path — the user's preference simply
     * ceased to exist, with nothing anywhere saying so. A preference that was refused is strictly
     * better than one that vanished.</p>
     *
     * @throws IllegalArgumentException when the key names no context, or no agent this platform has
     */
    public static ModelPreferenceKey fromStorageKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Model preference key must not be blank");
        }

        // Try as a plain TradingContext first (global key)
        for (TradingContext ctx : TradingContext.values()) {
            if (ctx.name().equals(key)) {
                return global(ctx);
            }
        }

        // Try as CONTEXT_AGENTKIND
        for (TradingContext ctx : TradingContext.values()) {
            String prefix = ctx.name() + "_";
            if (key.startsWith(prefix)) {
                String agentPart = key.substring(prefix.length());
                Optional<AgentKind> kind = AgentKind.fromName(agentPart);
                if (kind.isPresent()) {
                    return forAgent(ctx, kind.get());
                }
            }
        }

        throw new IllegalArgumentException("Unrecognised model preference key: '" + key
                + "'. Expected a trading context (e.g. BACKTESTING) optionally followed by an agent "
                + "(e.g. BACKTESTING_TECHNICAL, RESEARCH_STRATEGY_DESIGNER).");
    }

    @Override
    public String toString() {
        return toStorageKey();
    }
}
