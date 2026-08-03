package com.quorumtrading.adapter.featureflag.unleash;

import com.quorumtrading.application.port.out.FeatureFlagRepository;
import com.quorumtrading.model.entity.FeatureFlag;
import com.quorumtrading.model.valueobject.FeatureFlagStrategy;
import io.getunleash.ActivationStrategy;
import io.getunleash.FeatureToggle;
import io.getunleash.Segment;
import io.getunleash.repository.IFeatureRepository;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Feeds the Unleash SDK its toggle definitions straight from our own database.
 *
 * <p>Unleash normally polls an Unleash server over HTTP for definitions. Substituting this
 * implementation for the SDK's own {@code FeatureRepository} removes that server from the picture
 * entirely — the SDK keeps doing what it is good at (strategy, constraint and context evaluation)
 * while the toggles live in {@code feature_flags} where an administrator, or a self-hoster with
 * psql, can change them. No sidecar to run, no API token to manage, and a standalone install has
 * working flags out of the box.</p>
 *
 * <h2>Why there is a cache</h2>
 * <p>{@link #getToggle(String)} is called on every single evaluation, and some callers run on
 * Vert.x event-loop threads where a blocking JDBC round trip is not acceptable. The snapshot is
 * therefore refreshed off the calling thread: a stale-but-usable snapshot is served immediately
 * while a single background refresh runs. Only the very first call — during startup, on a worker
 * thread — blocks. The cost of that design is a bounded staleness window
 * ({@code quorumtrading.features.cache-ttl-seconds}, default 5s) between an administrator flipping
 * a flag and every node honouring it.</p>
 */
@ApplicationScoped
public class DatabaseToggleSource implements IFeatureRepository {

    private static final Logger LOG = Logger.getLogger(DatabaseToggleSource.class);

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private final ExecutorService refreshExecutor =
        Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "feature-flag-refresh");
            thread.setDaemon(true);
            return thread;
        });

    @Inject
    FeatureFlagRepository featureFlagRepository;

    @ConfigProperty(name = "quorumtrading.features.cache-ttl-seconds", defaultValue = "5")
    long cacheTtlSeconds;

    @Override
    public FeatureToggle getToggle(String name) {
        return currentToggles().get(name);
    }

    @Override
    public List<String> getFeatureNames() {
        return new ArrayList<>(currentToggles().keySet());
    }

    @Override
    public Segment getSegment(Integer id) {
        // Segments are an Unleash-server concept for sharing constraints between flags. We have no
        // way to define them, so every segment reference is unknown; returning null lets the SDK
        // treat it as unmatched rather than throwing.
        return null;
    }

    /**
     * Loads the current definitions synchronously. Called at startup once seeding has run, so the
     * first request never pays for the initial load.
     */
    public void warmUp() {
        loadSnapshot();
    }

    private Map<String, FeatureToggle> currentToggles() {
        Snapshot current = snapshot.get();
        if (current == null || cacheTtlSeconds <= 0) {
            // Nothing cached yet, or caching switched off — this caller waits. A TTL of zero means
            // "always read through", which tests rely on: flipping a flag and immediately calling
            // the API has to observe the new value, and a background refresh would not guarantee
            // that.
            return loadSnapshot().toggles();
        }
        if (current.isOlderThan(cacheTtlSeconds)) {
            refreshInBackground();
        }
        return current.toggles();
    }

    private void refreshInBackground() {
        if (!refreshInFlight.compareAndSet(false, true)) {
            return;
        }
        refreshExecutor.execute(() -> {
            try {
                loadSnapshot();
            } catch (RuntimeException e) {
                // Keep serving the previous snapshot. A database blip must not flip every flag.
                LOG.warnf(e, "Could not refresh feature flag definitions; serving the previous snapshot");
            } finally {
                refreshInFlight.set(false);
            }
        });
    }

    private synchronized Snapshot loadSnapshot() {
        Snapshot current = snapshot.get();
        String stamp = featureFlagRepository.versionStamp();
        if (current != null && stamp.equals(current.versionStamp())) {
            // Nothing changed — reset the clock without deserialising every row again.
            Snapshot extended = current.renewed();
            snapshot.set(extended);
            return extended;
        }

        Map<String, FeatureToggle> toggles = new LinkedHashMap<>();
        for (FeatureFlag flag : featureFlagRepository.findAll()) {
            toggles.put(flag.getFeature().flagName(), toToggle(flag));
        }
        Snapshot loaded = new Snapshot(Map.copyOf(toggles), stamp, System.nanoTime());
        snapshot.set(loaded);
        LOG.debugf("Loaded %d feature flag definitions (stamp %s)", toggles.size(), stamp);
        return loaded;
    }

    private static FeatureToggle toToggle(FeatureFlag flag) {
        List<ActivationStrategy> strategies = new ArrayList<>();
        for (FeatureFlagStrategy strategy : flag.getStrategies()) {
            strategies.add(new ActivationStrategy(strategy.name(), strategy.parameters()));
        }
        return new FeatureToggle(flag.getFeature().flagName(), flag.isEnabled(), strategies);
    }

    @PreDestroy
    void shutdown() {
        refreshExecutor.shutdownNow();
    }

    private record Snapshot(Map<String, FeatureToggle> toggles, String versionStamp, long loadedAtNanos) {

        boolean isOlderThan(long ttlSeconds) {
            return System.nanoTime() - loadedAtNanos > ttlSeconds * 1_000_000_000L;
        }

        Snapshot renewed() {
            return new Snapshot(toggles, versionStamp, System.nanoTime());
        }
    }
}
