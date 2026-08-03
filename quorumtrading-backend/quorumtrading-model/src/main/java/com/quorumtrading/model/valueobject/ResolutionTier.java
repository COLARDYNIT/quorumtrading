package com.quorumtrading.model.valueobject;

/**
 * Which tier of the model-resolution chain supplied the answer.
 *
 * <p>The tier is not bookkeeping — it decides what happens when the chosen model turns out to be
 * unservable. A model somebody explicitly asked for ({@link #isExplicitChoice() tiers 1–4}) must
 * fail loudly rather than be quietly swapped, because a backtest whose strategy pinned a model it
 * never ran is not a valid result. A system default is nobody's choice, so substituting the
 * engine's own model is the sensible outcome.</p>
 */
public enum ResolutionTier {

    /** Tier 1 — the strategy's per-agent pin ({@code llm_models.TECHNICAL}). */
    STRATEGY_AGENT(true),

    /** Tier 2 — the strategy's global pin ({@code llm_model}). */
    STRATEGY_GLOBAL(true),

    /** Tier 3 — the user's per-agent preference. */
    USER_AGENT(true),

    /** Tier 4 — the user's global preference for the context. */
    USER_GLOBAL(true),

    /** Tier 5 — the deployment's per-agent default. */
    SYSTEM_AGENT(false),

    /** Tier 6 — the deployment's context default. */
    SYSTEM_GLOBAL(false),

    /** Nothing was configured at any tier. */
    NONE(false);

    private final boolean explicitChoice;

    ResolutionTier(boolean explicitChoice) {
        this.explicitChoice = explicitChoice;
    }

    /**
     * Whether a human explicitly asked for this model, rather than inheriting a default.
     * An explicit choice that cannot be served is an error, not an invitation to guess.
     */
    public boolean isExplicitChoice() {
        return explicitChoice;
    }
}
