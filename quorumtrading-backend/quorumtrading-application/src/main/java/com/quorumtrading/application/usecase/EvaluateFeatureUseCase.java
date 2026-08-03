package com.quorumtrading.application.usecase;

import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.FeatureFlagContext;
import com.quorumtrading.model.valueobject.UserId;

import java.util.Map;

/**
 * Answers "is this feature on for this caller?".
 *
 * <p>This is the only way driving adapters are allowed to ask — REST filters, MCP tools and the
 * frontend's feature endpoint all go through here rather than reaching for the evaluation port,
 * so the fallback and logging behaviour is identical on every surface.</p>
 */
public interface EvaluateFeatureUseCase {

    boolean isEnabled(Feature feature, FeatureFlagContext context);

    /**
     * Throws {@link com.quorumtrading.application.exception.FeatureDisabledException} when the
     * feature is off for this caller. For guard clauses that want to fail rather than branch.
     */
    void requireEnabled(Feature feature, FeatureFlagContext context);

    /** Every registered feature evaluated against one context, for the frontend to render nav. */
    Map<Feature, Boolean> evaluateAll(FeatureFlagContext context);

    /**
     * Evaluates for a caller known only by id, resolving their email so that email-targeted flags
     * work the same way they do over REST.
     *
     * <p>Exists for MCP, where the token resolves to a {@link UserId} and nothing else. Doing the
     * lookup here rather than in the adapter keeps driving adapters out of {@code port.out} — and
     * keeps one definition of what a caller's flag context is.</p>
     *
     * @param userId the caller, or null for an unauthenticated one
     */
    boolean isEnabledForUser(Feature feature, UserId userId);

    /**
     * @throws com.quorumtrading.application.exception.FeatureDisabledException when off for them
     */
    void requireEnabledForUser(Feature feature, UserId userId);
}
