package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.FeatureFlag;
import com.quorumtrading.model.enums.Feature;

import java.util.List;
import java.util.Optional;

/**
 * Storage for feature flag state.
 *
 * <p>Note {@link #versionStamp()}: the Unleash SDK polls for definitions on a timer, and without a
 * cheap change probe every poll would deserialise every flag. The stamp lets the adapter answer
 * "nothing changed" from a single aggregate query.</p>
 */
public interface FeatureFlagRepository {

    List<FeatureFlag> findAll();

    Optional<FeatureFlag> find(Feature feature);

    FeatureFlag save(FeatureFlag featureFlag);

    /**
     * An opaque token that changes whenever any flag changes. Two equal stamps mean the flag set is
     * unchanged; callers must not read anything else into the value.
     */
    String versionStamp();
}
