package com.quorumtrading.bootstrap.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Validates security-critical configuration at startup.
 * Prevents accidental misconfiguration from reaching production.
 */
@ApplicationScoped
public class SecurityStartupValidator {

    private static final Logger LOG = Logger.getLogger(SecurityStartupValidator.class);

    @ConfigProperty(name = "quorumtrading.auth.dev-login-enabled", defaultValue = "false")
    boolean devLoginEnabled;

    @ConfigProperty(name = "quarkus.profile", defaultValue = "prod")
    String activeProfile;

    void onStart(@Observes StartupEvent ev) {
        if (devLoginEnabled && "prod".equals(activeProfile)) {
            throw new IllegalStateException(
                "FATAL: Dev-login is enabled in production profile! " +
                "This is a critical security misconfiguration. " +
                "Set quorumtrading.auth.dev-login-enabled=false for production."
            );
        }

        if (devLoginEnabled) {
            LOG.warnf("Dev-login is enabled (profile=%s). This must NEVER be enabled in production.", activeProfile);
        }
    }
}
