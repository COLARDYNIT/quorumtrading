package com.quorumtrading.adapter.featureflag.unleash;

import com.quorumtrading.application.port.out.FeatureFlagEvaluationPort;
import com.quorumtrading.model.enums.Feature;
import com.quorumtrading.model.valueobject.FeatureFlagContext;
import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.getunleash.util.UnleashConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Evaluates feature flags with the Unleash SDK against definitions held in our own database.
 *
 * <p>The SDK is wired to {@link DatabaseToggleSource} rather than to an Unleash server, so
 * nothing here talks to the network. The {@code unleashAPI} and {@code apiKey} settings below are
 * required by the config builder but never used — no HTTP client is constructed once the SDK has
 * been handed a repository, and metrics reporting is switched off.</p>
 *
 * <h2>Targeting a person</h2>
 * <p>The caller's email address is passed as Unleash's {@code userId}. That makes "on for one
 * person" the SDK's own built-in {@code userWithId} strategy with their address in {@code userIds},
 * with no custom strategy to write. The address is additionally exposed as an {@code email}
 * context property so constraints can match on it.</p>
 */
@ApplicationScoped
public class UnleashFeatureFlagEvaluator implements FeatureFlagEvaluationPort {

    private static final Logger LOG = Logger.getLogger(UnleashFeatureFlagEvaluator.class);

    private static final String APP_NAME = "quorumtrading";

    /** Never contacted; the SDK evaluates locally against {@link DatabaseToggleSource}. */
    private static final String UNUSED_API_URL = "http://localhost:4242/api/";
    private static final String UNUSED_API_KEY = "not-used-local-evaluation";

    @Inject
    DatabaseToggleSource toggleSource;

    @ConfigProperty(name = "quarkus.profile", defaultValue = "prod")
    String profile;

    private Unleash unleash;

    @PostConstruct
    void init() {
        UnleashConfig config = UnleashConfig.builder()
            .appName(APP_NAME)
            .instanceId(APP_NAME + "-" + profile)
            .environment(profile)
            .unleashAPI(UNUSED_API_URL)
            .apiKey(UNUSED_API_KEY)
            .disableMetrics()
            .build();
        this.unleash = new DefaultUnleash(config, toggleSource);
        LOG.debugf("Unleash evaluation initialised for environment '%s' with local definitions", profile);
    }

    @Override
    public boolean isEnabled(Feature feature, FeatureFlagContext context, boolean fallback) {
        try {
            return unleash.isEnabled(feature.flagName(), toUnleashContext(context), fallback);
        } catch (RuntimeException e) {
            // Evaluation must never be the reason a request fails. Falling back to the profile
            // default keeps a production feature on if the flag store is unreachable, rather than
            // turning the product off because of an infrastructure problem.
            LOG.warnf(e, "Feature flag '%s' could not be evaluated; falling back to %s",
                feature.flagName(), fallback);
            return fallback;
        }
    }

    private static UnleashContext toUnleashContext(FeatureFlagContext context) {
        UnleashContext.Builder builder = UnleashContext.builder();
        if (context == null) {
            return builder.build();
        }
        if (!context.isAnonymous()) {
            builder.userId(context.userEmail());
            builder.addProperty("email", context.userEmail());
        }
        if (context.userId() != null) {
            builder.addProperty("userId", context.userId().value().toString());
        }
        for (Map.Entry<String, String> property : context.properties().entrySet()) {
            builder.addProperty(property.getKey(), property.getValue());
        }
        return builder.build();
    }

    @PreDestroy
    void shutdown() {
        if (unleash != null) {
            unleash.shutdown();
        }
    }
}
