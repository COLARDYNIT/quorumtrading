package com.quorumtrading.bootstrap.adapter;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.model.enums.MarketDataFeature;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Discovery selects on capability and deliberately never reads credentials — that separation is
 * correct and stays. Being <em>switched off</em> is a different thing: a disabled adapter cannot
 * serve any request, so handing it to a caller only produces a wasted call and a provider name in
 * an error message that was never actually tried.
 */
@DisplayName("CdiMarketDataPortDiscovery")
class CdiMarketDataPortDiscoveryTest {

    private MarketDataPort enabledPort;
    private MarketDataPort disabledPort;

    private CdiMarketDataPortDiscovery discovery;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        enabledPort = describe("alpaca", true);
        disabledPort = describe("saxo", false);

        Instance<MarketDataPort> ports = mock(Instance.class);
        when(ports.spliterator())
            .thenAnswer(inv -> List.of(enabledPort, disabledPort).spliterator());

        discovery = new CdiMarketDataPortDiscovery();
        discovery.allMarketDataPorts = ports;
    }

    private MarketDataPort describe(String name, boolean enabled) {
        MarketDataPort port = mock(MarketDataPort.class);
        when(port.getProviderName()).thenReturn(name);
        when(port.isEnabled()).thenReturn(enabled);
        Set<MarketDataFeature> features = Set.of(
            MarketDataFeature.STOCK, MarketDataFeature.CANDLES, MarketDataFeature.BACKFILL);
        when(port.hasFeature(any())).thenAnswer(inv -> features.contains(inv.getArgument(0)));
        return port;
    }

    @Test
    @DisplayName("capability lookups skip a switched-off provider")
    void findAllByFeaturesExcludesDisabled() {
        List<MarketDataPort> found = discovery.findAllByFeatures(
            MarketDataFeature.BACKFILL, MarketDataFeature.CANDLES, MarketDataFeature.STOCK);

        assertThat(found).extracting(MarketDataPort::getProviderName).containsExactly("alpaca");
    }

    @Test
    @DisplayName("the single-provider lookup skips it too")
    void findByFeaturesExcludesDisabled() {
        assertThat(discovery.findByFeatures(MarketDataFeature.STOCK))
            .get()
            .extracting(MarketDataPort::getProviderName)
            .isEqualTo("alpaca");
    }

    @Test
    @DisplayName("findAll still returns it — a status view has to be able to say 'present, but off'")
    void findAllKeepsDisabledProviders() {
        assertThat(discovery.findAll())
            .extracting(MarketDataPort::getProviderName)
            .containsExactly("alpaca", "saxo");
    }

    @Test
    @DisplayName("lookup by name is unfiltered — asking for a provider by name means that provider")
    void findByProviderNameKeepsDisabledProviders() {
        assertThat(discovery.findByProviderName("saxo")).isPresent();
    }
}
