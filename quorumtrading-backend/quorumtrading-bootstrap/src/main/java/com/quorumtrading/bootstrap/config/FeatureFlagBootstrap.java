package com.quorumtrading.bootstrap.config;

import com.quorumtrading.adapter.featureflag.unleash.DatabaseToggleSource;
import com.quorumtrading.application.usecase.SeedFeatureFlagsUseCase;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Brings feature flags up before the first request is served.
 *
 * <p>Two steps, in this order: create rows for any feature that has none yet using the profile
 * defaults, then load those definitions into the evaluation cache. Warming the cache here means no
 * request thread — least of all a Vert.x event-loop thread handling an MCP {@code tools/list} —
 * ever has to make the first blocking read.</p>
 *
 * <p>A failure here is logged, not fatal. Every evaluation carries the profile default as its
 * fallback, so an instance that could not seed still behaves as its profile intends rather than
 * refusing to boot.</p>
 */
@ApplicationScoped
public class FeatureFlagBootstrap {

    private static final Logger LOG = Logger.getLogger(FeatureFlagBootstrap.class);

    @Inject
    SeedFeatureFlagsUseCase seedFeatureFlags;

    @Inject
    DatabaseToggleSource toggleSource;

    void onStart(@Observes StartupEvent event) {
        try {
            int created = seedFeatureFlags.execute();
            toggleSource.warmUp();
            if (created > 0) {
                LOG.infof("Feature flags ready: seeded %d new flag(s) from the profile defaults", created);
            } else {
                LOG.debug("Feature flags ready: all flags already present");
            }
        } catch (RuntimeException e) {
            LOG.warnf(e, "Could not initialise feature flags; falling back to the profile defaults");
        }
    }
}
