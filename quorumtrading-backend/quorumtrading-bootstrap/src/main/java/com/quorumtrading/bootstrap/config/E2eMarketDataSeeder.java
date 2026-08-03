package com.quorumtrading.bootstrap.config;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Seeds deterministic BTC market data (price bars + news) at startup for the core-loop
 * Playwright e2e (login → strategy → backtest → results).
 *
 * <p><b>Disabled by default</b> — gated on {@code quorumtrading.e2e.seed-market-data.enabled},
 * which only the {@code %e2e} profile flips on. It must never run in production.</p>
 *
 * <p>Why not a Flyway migration: {@code quarkus.flyway.locations} is a build-time property, so an
 * extra {@code %e2e}-only location cannot be added at runtime, and shipping the seed in the
 * default location would run it everywhere. This bean runs after Flyway migrate-at-start (Flyway
 * executes during runtime init, before {@link StartupEvent} observers fire).</p>
 *
 * <p>The seed script is idempotent (delete-before-insert scoped to {@code source='e2e-seed'}), so
 * backend restarts are safe. A failure logs loudly and aborts startup only in the sense that the
 * e2e will fail visibly — the exception is rethrown because an unseeded e2e backend is useless.</p>
 */
@ApplicationScoped
public class E2eMarketDataSeeder {

    private static final Logger LOG = Logger.getLogger(E2eMarketDataSeeder.class);
    private static final String SEED_RESOURCE = "e2e/seed-market-data.sql";

    @ConfigProperty(name = "quorumtrading.e2e.seed-market-data.enabled", defaultValue = "false")
    boolean enabled;

    @Inject
    AgroalDataSource dataSource;

    void onStart(@Observes StartupEvent ev) {
        if (!enabled) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(loadSeedSql());
            LOG.infof("E2E market data seeded from %s (source='e2e-seed')", SEED_RESOURCE);
        } catch (Exception e) {
            LOG.errorf(e, "E2E market data seeding failed: %s", e.getMessage());
            throw new IllegalStateException("E2E market data seeding failed", e);
        }
    }

    private String loadSeedSql() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(SEED_RESOURCE)) {
            if (is == null) {
                throw new IOException("Seed resource not found on classpath: " + SEED_RESOURCE);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
