package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.ModelTier;

/**
 * A model the active engine would actually run, offered to a caller whose pin it would not.
 *
 * <p>Annotated with the best tier the platform's own curation gives it and the context that earned
 * that tier, so a caller can tell a proven trading model from an untested one without a second
 * call.</p>
 *
 * @param id          the model id, exactly as the engine advertises it
 * @param tier        the best curated tier across all trading contexts, or {@code null} when the
 *                    model is simply untested
 * @param bestContext the trading context in which {@link #tier} was earned, {@code null} with tier
 * @param reason      short human-readable justification, or {@code null}
 */
public record ModelCandidate(String id, ModelTier tier, TradingContext bestContext, String reason) {

    public ModelCandidate {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Candidate model id cannot be null or blank");
        }
    }

    /** A candidate with no curation behind it — offered because the engine can serve it, nothing more. */
    public static ModelCandidate untested(String id) {
        return new ModelCandidate(id, null, null, null);
    }

    /** A candidate the platform has an opinion about. */
    public static ModelCandidate rated(String id, ModelTier tier, TradingContext context, String reason) {
        return new ModelCandidate(id, tier, context, reason);
    }

    /** Rendering used in error messages on both REST and MCP, so the two cannot drift apart. */
    public String describe() {
        if (tier == null) {
            return id + " (untested)";
        }
        return id + " (" + tier.name() + " for " + bestContext.name() + ")";
    }
}
