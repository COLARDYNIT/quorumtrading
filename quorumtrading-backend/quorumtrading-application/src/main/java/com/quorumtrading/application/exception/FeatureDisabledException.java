package com.quorumtrading.application.exception;

import com.quorumtrading.model.enums.Feature;

/**
 * Thrown when a caller reaches functionality whose feature flag is off for them.
 *
 * <p>The message deliberately names the flag. A refusal that only says "not available" sends the
 * reader hunting through configuration; naming the toggle turns it into a one-step fix, and the
 * flags gate whole product areas rather than anything security-sensitive, so there is nothing to
 * withhold. Surfaces as 403 over REST and as a tool error over MCP.</p>
 */
public class FeatureDisabledException extends RuntimeException {

    private final transient Feature feature;

    public FeatureDisabledException(Feature feature) {
        super(String.format(
            "The '%s' feature is not available on this instance. "
                + "An administrator can switch on the '%s' feature flag under Admin -> Feature flags.",
            feature.description(), feature.flagName()));
        this.feature = feature;
    }

    public Feature getFeature() {
        return feature;
    }

    /** The flag name an administrator has to toggle, e.g. {@code "challenges"}. */
    public String getFlagName() {
        return feature.flagName();
    }
}
