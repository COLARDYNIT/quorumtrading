package com.quorumtrading.model.enums;

/**
 * Categorization tier for LLM models based on A/B testing results.
 * <p>
 * Tiers are ordered from best to worst for display purposes:
 * RECOMMENDED > PROVEN > BUDGET > EXPERIMENTAL > AVOID
 */
public enum ModelTier {

    /**
     * Best-in-class for this context. Tested with strong results.
     */
    RECOMMENDED,

    /**
     * Tested and working reliably. Good alternative to recommended.
     */
    PROVEN,

    /**
     * Cheap and from a trusted provider, but not specifically tested.
     */
    BUDGET,

    /**
     * Unknown model — not tested, use at your own risk.
     */
    EXPERIMENTAL,

    /**
     * Known to fail or perform poorly. Should not be used.
     */
    AVOID
}
