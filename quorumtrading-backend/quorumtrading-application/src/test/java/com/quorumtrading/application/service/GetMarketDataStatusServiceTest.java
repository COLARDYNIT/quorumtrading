package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.application.port.out.MarketDataPortDiscovery;
import com.quorumtrading.application.usecase.marketdata.MarketDataStatus;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.marketdata.MarketEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The question a self-hoster could not ask from inside the product: "do I have working price data,
 * and for what?"
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetMarketDataStatusService")
class GetMarketDataStatusServiceTest {

    @Mock private MarketDataPortDiscovery discovery;
    @Mock private MarketDataPort cryptoProvider;
    @Mock private MarketDataPort stockProvider;
    @Mock private MarketDataPort newsProvider;

    private GetMarketDataStatusService service;

    @BeforeEach
    void setUp() {
        service = new GetMarketDataStatusService(discovery);
    }

    /** Mockito does not run default interface methods, so every flag has to be stubbed explicitly. */
    private void describe(MarketDataPort port, String name, boolean enabled, boolean configured,
                          MarketDataFeature... features) {
        lenient().when(port.getProviderName()).thenReturn(name);
        lenient().when(port.isEnabled()).thenReturn(enabled);
        lenient().when(port.isConfigured()).thenReturn(configured);
        Set<MarketDataFeature> featureSet = Set.of(features);
        lenient().when(port.getSupportedFeatures()).thenReturn(featureSet);
        lenient().when(port.hasFeature(any())).thenAnswer(inv -> featureSet.contains(inv.getArgument(0)));
    }

    private void keylessCryptoProvider(boolean enabled) {
        describe(cryptoProvider, "kraken", enabled, true,
            MarketDataFeature.CRYPTO, MarketDataFeature.CANDLES, MarketDataFeature.BACKFILL);
    }

    private void stockProvider(boolean enabled, boolean configured) {
        describe(stockProvider, "alpaca", enabled, configured,
            MarketDataFeature.STOCK, MarketDataFeature.CANDLES, MarketDataFeature.BACKFILL);
    }

    @Test
    @DisplayName("reports a switched-off provider rather than hiding it")
    void reportsDisabledProviders() {
        keylessCryptoProvider(true);
        stockProvider(false, true);
        when(discovery.findAll()).thenReturn(List.of(cryptoProvider, stockProvider));

        MarketDataStatus status = service.get(false);

        assertThat(status.providers()).extracting(MarketDataStatus.ProviderStatus::name)
            .as("'that provider is off' is the answer to 'why is my stock backfill failing?'")
            .containsExactly("kraken", "alpaca");
        assertThat(status.providers().get(1).enabled()).isFalse();
        assertThat(status.providers().get(1).usable()).isFalse();
        assertThat(status.stockUsable()).isFalse();
        assertThat(status.cryptoUsable()).isTrue();
    }

    @Test
    @DisplayName("keeps enabled and credentialed apart — Saxo is one, the keyless sources are the other")
    void enabledAndCredentialedAreDistinct() {
        keylessCryptoProvider(true);
        stockProvider(true, false);
        when(discovery.findAll()).thenReturn(List.of(cryptoProvider, stockProvider));

        MarketDataStatus status = service.get(false);

        MarketDataStatus.ProviderStatus alpaca = status.providers().get(1);
        assertThat(alpaca.enabled()).isTrue();
        assertThat(alpaca.credentialed()).isFalse();
        assertThat(alpaca.usable()).isFalse();
    }

    @Test
    @DisplayName("ignores news and social sources — a different question, asked elsewhere")
    void ignoresNonCandleProviders() {
        describe(newsProvider, "rss", true, true, MarketDataFeature.NEWS, MarketDataFeature.BACKFILL);
        when(discovery.findAll()).thenReturn(List.of(newsProvider));

        assertThat(service.get(false).providers()).isEmpty();
    }

    @Test
    @DisplayName("a live probe that returns bars is the only proof a key still works")
    void probeReportsSuccess() {
        keylessCryptoProvider(true);
        when(discovery.findAll()).thenReturn(List.of(cryptoProvider));
        when(cryptoProvider.fetchDataInRange(any(), any(), any(), any(), any()))
            .thenReturn(List.of(org.mockito.Mockito.mock(MarketEvent.class)));

        MarketDataStatus.Probe probe = service.get(true).providers().get(0).probe();

        assertThat(probe).isNotNull();
        assertThat(probe.symbol()).isEqualTo(GetMarketDataStatusService.CRYPTO_PROBE_SYMBOL);
        assertThat(probe.ok()).isTrue();
    }

    @Test
    @DisplayName("an empty answer is not a healthy one — a present key can still be unentitled")
    void probeReportsAnEmptyAnswerAsAFailure() {
        stockProvider(true, true);
        when(discovery.findAll()).thenReturn(List.of(stockProvider));
        when(stockProvider.fetchDataInRange(any(), any(), any(), any(), any())).thenReturn(List.of());

        MarketDataStatus.Probe probe = service.get(true).providers().get(0).probe();

        assertThat(probe.ok()).isFalse();
        assertThat(probe.detail()).contains("not entitled");
    }

    @Test
    @DisplayName("a throwing provider is reported, without the framework noise")
    void probeSummarisesAFailure() {
        stockProvider(true, true);
        when(discovery.findAll()).thenReturn(List.of(stockProvider));
        when(stockProvider.fetchDataInRange(any(), any(), any(), any(), any()))
            .thenThrow(new RuntimeException("alpaca returned HTTP 401, status code 401 when invoking "
                + "REST Client method: 'com.quorumtrading.adapter.marketdata.alpaca.client.X#getBars'"));

        MarketDataStatus.Probe probe = service.get(true).providers().get(0).probe();

        assertThat(probe.ok()).isFalse();
        assertThat(probe.detail())
            .contains("401")
            .doesNotContain("com.quorumtrading")
            .doesNotContain("REST Client method");
    }

    @Test
    @DisplayName("does not probe a provider it already knows cannot answer")
    void doesNotProbeUnusableProviders() {
        stockProvider(true, false);
        when(discovery.findAll()).thenReturn(List.of(stockProvider));

        assertThat(service.get(true).providers().get(0).probe()).isNull();
        verify(stockProvider, never()).fetchDataInRange(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("probe=false makes no outbound call at all")
    void probeCanBeSkipped() {
        keylessCryptoProvider(true);
        when(discovery.findAll()).thenReturn(List.of(cryptoProvider));

        assertThat(service.get(false).providers().get(0).probe()).isNull();
        verify(cryptoProvider, never()).fetchDataInRange(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a deployment with nothing usable says so at the top level")
    void nothingUsable() {
        stockProvider(false, false);
        when(discovery.findAll()).thenReturn(List.of(stockProvider));

        MarketDataStatus status = service.get(true);

        assertThat(status.cryptoUsable()).isFalse();
        assertThat(status.stockUsable()).isFalse();
    }
}
