package com.quorumtrading.adapter.marketdata.saxo;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.model.enums.MarketDataFeature;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Saxo market-data adapter is <b>discoverable as a {@link MarketDataPort}</b> the way the
 * runtime {@code CdiMarketDataPortDiscovery} (bootstrap layer) discovers it: by provider name and by
 * capability. The discovery walks CDI {@code Instance<MarketDataPort>} filtering on
 * {@code getProviderName()} and {@code hasFeature(...)}, so this test exercises the same two lookups
 * against the real bean in this isolated module — without crossing the adapter→bootstrap boundary
 * (adapters must not depend on each other).
 *
 * <ul>
 *   <li><b>by name</b> — {@code "saxo"} resolves to the {@link SaxoMarketDataAdapter} (and the bean
 *       also carries the matching {@code @Named("saxo")} qualifier);</li>
 *   <li><b>by feature</b> — a {@code STOCK + EU_INSTRUMENT} capability search finds it (this is the
 *       capability MD4 routes European-venue instruments on);</li>
 *   <li><b>provider identity</b> — {@code getProviderName()} is {@code "saxo"}.</li>
 * </ul>
 *
 * <p>No HTTP is performed; this is pure CDI wiring + capability advertisement.</p>
 */
@QuarkusTest
class SaxoMarketDataDiscoveryTest {

    @Inject
    Instance<MarketDataPort> allMarketDataPorts;

    @Inject
    @Named("saxo")
    MarketDataPort namedSaxo;

    /** The same lookup {@code CdiMarketDataPortDiscovery.findByProviderName} performs. */
    private Optional<MarketDataPort> findByProviderName(String providerName) {
        return StreamSupport.stream(allMarketDataPorts.spliterator(), false)
                .filter(p -> providerName.equals(p.getProviderName()))
                .findFirst();
    }

    /** The same lookup {@code CdiMarketDataPortDiscovery.findByFeatures} performs (ALL features). */
    private Optional<MarketDataPort> findByFeatures(MarketDataFeature... features) {
        return StreamSupport.stream(allMarketDataPorts.spliterator(), false)
                .filter(p -> {
                    for (MarketDataFeature f : features) {
                        if (!p.hasFeature(f)) {
                            return false;
                        }
                    }
                    return true;
                })
                .findFirst();
    }

    @Test
    void saxoIsDiscoverableByProviderName() {
        Optional<MarketDataPort> found = findByProviderName("saxo");

        assertThat(found).isPresent();
        assertThat(found.get()).isInstanceOf(SaxoMarketDataAdapter.class);
        assertThat(found.get().getProviderName()).isEqualTo("saxo");
    }

    @Test
    void saxoIsDiscoverableByEuInstrumentCapability() {
        // MD4 routes European-venue instruments to a STOCK + EU_INSTRUMENT capable provider.
        Optional<MarketDataPort> found =
                findByFeatures(MarketDataFeature.STOCK, MarketDataFeature.EU_INSTRUMENT);

        assertThat(found).isPresent();
        assertThat(found.get().getProviderName()).isEqualTo("saxo");
    }

    @Test
    void saxoAdvertisesStockAndEuInstrumentFeatures() {
        MarketDataPort saxo = findByProviderName("saxo").orElseThrow();

        assertThat(saxo.getSupportedFeatures())
                .contains(MarketDataFeature.STOCK, MarketDataFeature.EU_INSTRUMENT);
        assertThat(saxo.hasFeature(MarketDataFeature.EU_INSTRUMENT)).isTrue();
    }

    @Test
    void saxoBeanCarriesNamedSaxoQualifier() {
        // The @Named("saxo") qualifier on the adapter must resolve to the same provider.
        assertThat(namedSaxo).isInstanceOf(SaxoMarketDataAdapter.class);
        assertThat(namedSaxo.getProviderName()).isEqualTo("saxo");
    }

    /**
     * Saxo is off by default in every deployment profile, and until it could be <em>asked</em> that
     * fact was invisible from outside: a failed stock backfill reported "Tried 3 configured
     * provider(s): alpaca, tiingo, saxo" on a container running {@code SAXO_ENABLED=false} — the
     * one message a user has for telling missing credentials apart from missing coverage.
     *
     * <p>Asserted against a directly-constructed adapter rather than the injected bean, because
     * this module's own test profile sets {@code quorumtrading.marketdata.saxo.enabled=true} so the
     * WireMock tests can exercise the fetch paths. Reading the injected bean would only prove the
     * test profile's value; what matters is that the answer tracks the flag at all, instead of
     * being the interface default of {@code true}.</p>
     */
    @Test
    void saxoReportsWhetherItIsSwitchedOn() {
        SaxoMarketDataAdapter off = new SaxoMarketDataAdapter();
        off.enabled = false;
        SaxoMarketDataAdapter on = new SaxoMarketDataAdapter();
        on.enabled = true;

        assertThat(off.isEnabled()).isFalse();
        assertThat(on.isEnabled()).isTrue();
    }

    @Test
    void exactlyOneSaxoProviderIsRegistered() {
        List<MarketDataPort> saxoPorts = StreamSupport.stream(allMarketDataPorts.spliterator(), false)
                .filter(p -> "saxo".equals(p.getProviderName()))
                .toList();

        assertThat(saxoPorts).hasSize(1);
    }
}
