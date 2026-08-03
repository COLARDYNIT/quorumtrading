package com.quorumtrading.model.enums;

import com.quorumtrading.model.valueobject.ResearchAgentType;

import java.util.Optional;

/**
 * Every kind of agent a model preference can be expressed for — trading and research alike.
 *
 * <p>Consensus agents are {@link AgentRole}; research agents are {@link ResearchAgentType}. They are
 * genuinely different vocabularies, which is why research could not reuse the per-agent tiers and
 * ended up with a resolver of its own — and why a per-agent research preference was storable but
 * silently ignored. One superset over both gives trading and research a single preference model and
 * a single resolution chain, so {@code BACKTESTING_TECHNICAL} and {@code RESEARCH_STRATEGY_DESIGNER}
 * parse through the same path.</p>
 *
 * <p><b>Storage compatibility is deliberate and load-bearing.</b> Every constant here is named
 * exactly as its {@code AgentRole} or {@code ResearchAgentType} counterpart, so keys already
 * persisted in {@code user_budgets.model_preferences} keep parsing unchanged. Renaming one would
 * silently orphan stored preferences, which is worse than refusing them.</p>
 */
public enum AgentKind {

    // ── Consensus agents (AgentRole) ────────────────────────────────────────
    ORCHESTRATOR,
    TECHNICAL,
    FUNDAMENTAL,
    SENTIMENT,

    // ── Research agents (ResearchAgentType) ─────────────────────────────────
    NEWS_SENTIMENT_ANALYST,
    STRATEGY_DESIGNER,
    VIABILITY_ASSESSOR;

    /** The kind corresponding to a consensus agent role. */
    public static AgentKind of(AgentRole role) {
        return role == null ? null : valueOf(role.name());
    }

    /** The kind corresponding to a research agent type. */
    public static AgentKind of(ResearchAgentType type) {
        return type == null ? null : valueOf(type.name());
    }

    /** Parse a stored name, empty when it names no agent this platform knows. */
    public static Optional<AgentKind> fromName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        for (AgentKind kind : values()) {
            if (kind.name().equals(name)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    /** The consensus role this kind denotes, empty for a research agent. */
    public Optional<AgentRole> asAgentRole() {
        for (AgentRole role : AgentRole.values()) {
            if (role.name().equals(name())) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }

    /** The research agent this kind denotes, empty for a consensus agent. */
    public Optional<ResearchAgentType> asResearchAgentType() {
        for (ResearchAgentType type : ResearchAgentType.values()) {
            if (type.name().equals(name())) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** Whether this kind belongs to the consensus vocabulary. */
    public boolean isConsensusAgent() {
        return asAgentRole().isPresent();
    }

    /** Whether this kind belongs to the research vocabulary. */
    public boolean isResearchAgent() {
        return asResearchAgentType().isPresent();
    }
}
