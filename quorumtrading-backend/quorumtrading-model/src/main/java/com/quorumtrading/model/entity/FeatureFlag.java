package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.FeatureFlagStrategy;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The persisted on/off state of one {@link Feature}, plus who it is on for.
 *
 * <p>The pair (enabled, strategies) is Unleash's model and it is kept intact here rather than
 * flattened to a boolean: {@code enabled} is the master switch, and the strategies decide who
 * a switched-on flag actually applies to. A flag that is on with no strategies would evaluate to
 * off in Unleash, which reads as a broken toggle to whoever flipped it, so
 * {@link #changeTo(boolean, List, UserId)} substitutes the "everyone" strategy in that case.</p>
 */
public class FeatureFlag {

    private final Feature feature;
    private boolean enabled;
    private List<FeatureFlagStrategy> strategies;
    private Instant updatedAt;
    private UserId updatedBy;

    private FeatureFlag(Feature feature,
                        boolean enabled,
                        List<FeatureFlagStrategy> strategies,
                        Instant updatedAt,
                        UserId updatedBy) {
        if (feature == null) {
            throw new IllegalArgumentException("Feature is required");
        }
        this.feature = feature;
        this.enabled = enabled;
        this.strategies = normalise(enabled, strategies);
        this.updatedAt = updatedAt == null ? Instant.now() : updatedAt;
        this.updatedBy = updatedBy;
    }

    /** A freshly seeded flag, on or off for everyone, with no author. */
    public static FeatureFlag seed(Feature feature, boolean enabled) {
        return new FeatureFlag(feature, enabled, List.of(), Instant.now(), null);
    }

    /** Rehydration from storage. */
    public static FeatureFlag reconstitute(Feature feature,
                                           boolean enabled,
                                           List<FeatureFlagStrategy> strategies,
                                           Instant updatedAt,
                                           UserId updatedBy) {
        return new FeatureFlag(feature, enabled, strategies, updatedAt, updatedBy);
    }

    /**
     * Applies an administrator's change.
     *
     * @param enabled    the master switch
     * @param strategies who the flag applies to; empty means everyone when {@code enabled}
     * @param changedBy  the administrator making the change, recorded for audit
     */
    public void changeTo(boolean enabled, List<FeatureFlagStrategy> strategies, UserId changedBy) {
        this.enabled = enabled;
        this.strategies = normalise(enabled, strategies);
        this.updatedAt = Instant.now();
        this.updatedBy = changedBy;
    }

    private static List<FeatureFlagStrategy> normalise(boolean enabled, List<FeatureFlagStrategy> strategies) {
        List<FeatureFlagStrategy> cleaned = new ArrayList<>();
        if (strategies != null) {
            for (FeatureFlagStrategy strategy : strategies) {
                if (strategy != null) {
                    cleaned.add(strategy);
                }
            }
        }
        if (enabled && cleaned.isEmpty()) {
            cleaned.add(FeatureFlagStrategy.everyone());
        }
        return Collections.unmodifiableList(cleaned);
    }

    /**
     * True when this flag is on for every caller, so no evaluation context is needed.
     * Used to answer the common case without paying for a full Unleash evaluation.
     */
    public boolean isOnForEveryone() {
        return enabled && strategies.stream().anyMatch(FeatureFlagStrategy::isUnconditional);
    }

    public Feature getFeature() {
        return feature;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<FeatureFlagStrategy> getStrategies() {
        return strategies;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UserId getUpdatedBy() {
        return updatedBy;
    }
}
