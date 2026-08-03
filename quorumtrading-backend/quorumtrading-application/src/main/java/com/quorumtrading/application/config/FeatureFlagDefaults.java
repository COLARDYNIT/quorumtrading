package com.quorumtrading.application.config;

import com.quorumtrading.model.enums.Feature;

import java.util.EnumMap;
import java.util.Map;

/**
 * The per-profile starting position for each flag.
 *
 * <p>These are seeds, not overrides. On first boot a missing flag row is created with the value
 * here; from then on the database wins and an administrator's change survives restarts. They are
 * also the fallback answer when a flag cannot be evaluated, so that an outage in the evaluation
 * path degrades to the profile's intent rather than to "everything off".</p>
 */
public record FeatureFlagDefaults(Map<Feature, Boolean> values) {

    public FeatureFlagDefaults {
        Map<Feature, Boolean> copy = new EnumMap<>(Feature.class);
        if (values != null) {
            copy.putAll(values);
        }
        for (Feature feature : Feature.values()) {
            copy.putIfAbsent(feature, Boolean.FALSE);
        }
        values = Map.copyOf(copy);
    }

    public boolean defaultFor(Feature feature) {
        return Boolean.TRUE.equals(values.get(feature));
    }
}
