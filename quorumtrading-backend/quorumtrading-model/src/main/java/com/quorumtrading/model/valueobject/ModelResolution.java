package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.LlmProvider;

/**
 * The answer to "which model will actually run, and why?" — computed once and read by every
 * surface that claims to know.
 *
 * <p>Three surfaces used to answer this question three different ways, and at least one of them
 * lied. This record is the single computed value they now share, so a disagreement between
 * {@code get_model_preferences}, the settings page and {@code llm_decisions} becomes impossible by
 * construction rather than by vigilance.</p>
 *
 * @param requested          the model the resolution chain chose, or {@code null} when nothing was
 *                           configured at any tier
 * @param effective          the model that will actually be sent to the engine; {@code null} when
 *                           the request cannot be served at all, which reads as a diagnosis rather
 *                           than a crash
 * @param decidedByTier      which tier supplied {@link #requested}
 * @param engine             the engine that will serve the call, or {@code null} when undetermined
 * @param substitutionReason why {@link #effective} differs from {@link #requested}, or {@code null}
 *                           when it does not
 * @param effort             requested reasoning effort, a dimension separate from which model;
 *                           {@code null} when none was expressed, in which case an engine that
 *                           supports effort must omit the flag rather than invent a level
 */
public record ModelResolution(
        String requested,
        String effective,
        ResolutionTier decidedByTier,
        LlmProvider engine,
        String substitutionReason,
        ModelEffort effort
) {

    public ModelResolution {
        if (decidedByTier == null) {
            decidedByTier = ResolutionTier.NONE;
        }
    }

    /** A resolution without an effort level. */
    public ModelResolution(String requested, String effective, ResolutionTier decidedByTier,
                           LlmProvider engine, String substitutionReason) {
        this(requested, effective, decidedByTier, engine, substitutionReason, null);
    }

    /** The same resolution carrying a reasoning effort. */
    public ModelResolution withEffort(ModelEffort effort) {
        return new ModelResolution(requested, effective, decidedByTier, engine, substitutionReason, effort);
    }

    /** The engine serves exactly what was asked for. */
    public static ModelResolution served(String model, ResolutionTier tier, LlmProvider engine) {
        return new ModelResolution(model, model, tier, engine, null);
    }

    /** The engine cannot serve the request, and a defined substitute was used instead. */
    public static ModelResolution substituted(String requested, String actual, ResolutionTier tier,
                                              LlmProvider engine, String reason) {
        return new ModelResolution(requested, actual, tier, engine, reason);
    }

    /** The engine cannot serve the request and substituting would misrepresent the result. */
    public static ModelResolution rejected(String requested, ResolutionTier tier,
                                           LlmProvider engine, String reason) {
        return new ModelResolution(requested, null, tier, engine, reason);
    }

    /** Nothing was configured at any tier. */
    public static ModelResolution unresolved(LlmProvider engine) {
        return new ModelResolution(null, null, ResolutionTier.NONE, engine,
                "no model configured at any tier");
    }

    /** Whether a model came out of this at all. */
    public boolean isServable() {
        return effective != null && !effective.isBlank();
    }

    /** Whether the model that runs differs from the one that was asked for. */
    public boolean wasSubstituted() {
        return isServable() && requested != null && !requested.equals(effective);
    }
}
