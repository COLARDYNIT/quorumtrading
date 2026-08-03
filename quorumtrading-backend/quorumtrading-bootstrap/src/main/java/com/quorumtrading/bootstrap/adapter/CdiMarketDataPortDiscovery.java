package com.quorumtrading.bootstrap.adapter;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.application.port.out.MarketDataPortDiscovery;
import com.quorumtrading.model.enums.MarketDataFeature;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * CDI-based implementation of MarketDataPortDiscovery.
 *
 * Uses CDI Instance&lt;MarketDataPort&gt; to dynamically discover all registered
 * MarketDataPort implementations at runtime. This adapter lives in the bootstrap
 * layer, keeping CDI dependencies out of the application layer.
 *
 * Registered in ApplicationConfig as a producer method.
 */
@ApplicationScoped
public class CdiMarketDataPortDiscovery implements MarketDataPortDiscovery {

    private static final Logger LOG = Logger.getLogger(CdiMarketDataPortDiscovery.class);

    @Inject
    Instance<MarketDataPort> allMarketDataPorts;

    @Override
    public Optional<MarketDataPort> findByProviderName(String providerName) {
        return StreamSupport.stream(allMarketDataPorts.spliterator(), false)
            .filter(port -> providerName.equals(port.getProviderName()))
            .findFirst();
    }

    @Override
    public List<MarketDataPort> findAll() {
        return StreamSupport.stream(allMarketDataPorts.spliterator(), false)
            .toList();
    }

    @Override
    public Optional<MarketDataPort> findByFeatures(MarketDataFeature... requiredFeatures) {
        if (requiredFeatures == null || requiredFeatures.length == 0) {
            LOG.warn("findByFeatures called with no features, returning first available provider");
            return StreamSupport.stream(allMarketDataPorts.spliterator(), false).findFirst();
        }

        Optional<MarketDataPort> result = StreamSupport.stream(allMarketDataPorts.spliterator(), false)
            .filter(MarketDataPort::isEnabled)
            .filter(port -> supportsAllFeatures(port, requiredFeatures))
            .findFirst();

        if (result.isEmpty()) {
            LOG.warnf("No provider found supporting features: %s", Arrays.toString(requiredFeatures));
        } else {
            LOG.debugf("Found provider %s for features: %s",
                result.get().getProviderName(), Arrays.toString(requiredFeatures));
        }

        return result;
    }

    @Override
    public List<MarketDataPort> findAllByFeatures(MarketDataFeature... requiredFeatures) {
        if (requiredFeatures == null || requiredFeatures.length == 0) {
            return findAll();
        }

        List<MarketDataPort> result = StreamSupport.stream(allMarketDataPorts.spliterator(), false)
            .filter(MarketDataPort::isEnabled)
            .filter(port -> supportsAllFeatures(port, requiredFeatures))
            .toList();

        LOG.debugf("Found %d providers supporting features: %s",
            result.size(), Arrays.toString(requiredFeatures));

        return result;
    }

    /**
     * Check if a port supports all required features.
     *
     * <p>Capability lookups additionally exclude disabled adapters (see
     * {@link MarketDataPort#isEnabled()}), because a provider that is switched off cannot serve
     * the request and must not be counted among those "tried". {@link #findAll()} deliberately
     * does not filter — the status view has to be able to say that a provider exists and is off.</p>
     */
    private boolean supportsAllFeatures(MarketDataPort port, MarketDataFeature... requiredFeatures) {
        for (MarketDataFeature feature : requiredFeatures) {
            if (!port.hasFeature(feature)) {
                return false;
            }
        }
        return true;
    }
}
