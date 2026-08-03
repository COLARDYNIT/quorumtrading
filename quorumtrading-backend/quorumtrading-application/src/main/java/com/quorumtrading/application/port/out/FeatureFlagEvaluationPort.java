package com.quorumtrading.application.port.out;

import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.FeatureFlagContext;

/**
 * Decides whether a feature is on for a given caller.
 *
 * <p>Implemented by the Unleash adapter, which owns strategy and constraint evaluation. Kept
 * separate from {@link FeatureFlagRepository} so the application layer never has to know that
 * "enabled plus strategies" is more than a boolean.</p>
 */
public interface FeatureFlagEvaluationPort {

    /**
     * @param fallback answer to use when the flag has no definition yet — during startup, before
     *                 seeding has run, or if the evaluation engine is unavailable. Callers pass the
     *                 configured per-profile default so an evaluation failure cannot silently turn a
     *                 production feature off.
     */
    boolean isEnabled(Feature feature, FeatureFlagContext context, boolean fallback);
}
