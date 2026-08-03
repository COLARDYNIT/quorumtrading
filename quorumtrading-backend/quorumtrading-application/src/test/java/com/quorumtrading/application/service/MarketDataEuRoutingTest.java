package com.quorumtrading.application.service;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.marketdata.RateLimit;
import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.model.valueobject.MIC;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * MD4 routing: a European-MIC gap PREFERS an instrument-covering provider (Saxo) while US/crypto
 * gaps stay byte-identical (no reorder, {@code coversInstrument} never consulted).
 *
 * <p>Exercised through {@link DataHorizonProbeService}, whose {@code probeEarliest} probes
 * {@code providers.get(0)} only — so the head of the selected list is directly observable and a
 * proxy for which provider {@code BackfillExecutor} would try first in its fallback chain (both
 * share the identical {@code preferInstrumentCoveringProviders} contract).</p>
 */
@ExtendWith(MockitoExtension.class)
class MarketDataEuRoutingTest {

    @Mock
    RateLimitTracker rateLimitTracker;

    /** A US-default provider (Alpaca-like): feature-matches but is NOT instrument-aware. */
    private MarketDataPort usProvider;

    /** An EU venue provider (Saxo-like): feature-matches AND covers European instruments. */
    private MarketDataPort saxoProvider;

    private static final Symbol RHM_XETR = new Symbol(
            "RHM", InvestmentType.STOCK, new MIC("XETR"), Money.Currency.EUR, null, null);
    private static final Symbol AAPL = new Symbol("AAPL", InvestmentType.STOCK);
    private static final Symbol BTC = new Symbol("BTC", InvestmentType.CRYPTO);

    @BeforeEach
    void setUp() {
        usProvider = mock(MarketDataPort.class);
        lenient().when(usProvider.getProviderName()).thenReturn("alpaca");
        lenient().when(usProvider.getSupportedFeatures()).thenReturn(Set.of(
                MarketDataFeature.BACKFILL, MarketDataFeature.STOCK,
                MarketDataFeature.CRYPTO, MarketDataFeature.CANDLES));
        lenient().when(usProvider.hasFeature(any())).thenAnswer(inv ->
                usProvider.getSupportedFeatures().contains((MarketDataFeature) inv.getArgument(0)));
        lenient().when(usProvider.getRateLimit()).thenReturn(RateLimit.perMinute(200));
        // Default boolean stub: a US-default provider claims to cover everything (interface default
        // is true; here we make the stub explicit so we can prove it is NOT called for US symbols).
        lenient().when(usProvider.coversInstrument(any())).thenReturn(true);

        saxoProvider = mock(MarketDataPort.class);
        lenient().when(saxoProvider.getProviderName()).thenReturn("saxo");
        lenient().when(saxoProvider.getSupportedFeatures()).thenReturn(Set.of(
                MarketDataFeature.BACKFILL, MarketDataFeature.STOCK,
                MarketDataFeature.CANDLES, MarketDataFeature.EU_INSTRUMENT));
        lenient().when(saxoProvider.hasFeature(any())).thenAnswer(inv ->
                saxoProvider.getSupportedFeatures().contains((MarketDataFeature) inv.getArgument(0)));
        lenient().when(saxoProvider.getRateLimit()).thenReturn(RateLimit.perMinute(120));
        // Saxo covers only European instruments.
        lenient().when(saxoProvider.coversInstrument(any())).thenAnswer(inv ->
                ((Symbol) inv.getArgument(0)).isEuropean());

        lenient().when(rateLimitTracker.wouldExceedLimit(any(), any())).thenReturn(false);
    }

    /** Make a provider report data for every month (so probeEarliest converges in one anchor hit). */
    private void hasDataAlways(MarketDataPort provider, String source, String ticker) {
        // Build the canned event BEFORE the when()/thenReturn stubbing so no mock call (e.g.
        // getProviderName) happens mid-stubbing — that triggers Mockito UnfinishedStubbing.
        MarketEvent canned = new MarketEvent(
                LocalDate.now().atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                EventType.PRICE, source, ticker, Map.of("close", 100.0));
        lenient().when(provider.fetchDataInRange(any(), any(), any(), any(), any(Granularity.class)))
                .thenReturn(List.of(canned));
    }

    @Test
    void euSymbolPrefersSaxoEvenWhenUsDefaultAlsoClaimsCoverage() {
        // US provider listed FIRST and (like the interface default) claims to cover everything.
        // EU preference must still promote Saxo — the venue-native provider that advertises
        // EU_INSTRUMENT — so the EU name is probed from where it trades, not the US default.
        DataHorizonProbeService probe = new DataHorizonProbeService(
                List.of(usProvider, saxoProvider), rateLimitTracker);
        hasDataAlways(saxoProvider, "saxo", "RHM");

        Optional<LocalDate> earliest = probe.probeEarliest(RHM_XETR, EventType.PRICE);

        // Saxo (venue-native) was preferred to the head and probed; the US provider was not probed.
        assertThat(earliest).isPresent();
        verify(saxoProvider, atLeastOnce()).fetchDataInRange(any(), any(), any(), any(), any(Granularity.class));
        verify(usProvider, never()).fetchDataInRange(any(), any(), any(), any(), any(Granularity.class));
    }

    @Test
    void usSymbolKeepsOriginalOrder_usProviderProbed_coverageNeverConsulted() {
        // For a US symbol the list is NOT reordered and coversInstrument is never consulted —
        // byte-identical to pre-MD4 routing.
        DataHorizonProbeService probe = new DataHorizonProbeService(
                List.of(usProvider, saxoProvider), rateLimitTracker);
        hasDataAlways(usProvider, "alpaca", "AAPL");

        Optional<LocalDate> earliest = probe.probeEarliest(AAPL, EventType.PRICE);

        assertThat(earliest).isPresent();
        verify(usProvider, atLeastOnce()).fetchDataInRange(any(), any(), any(), any(), any(Granularity.class));
        verify(saxoProvider, never()).fetchDataInRange(any(), any(), any(), any(), any(Granularity.class));
        // Byte-identical US routing: the EU-preference partition must not touch coversInstrument.
        verify(usProvider, never()).coversInstrument(any());
        verify(saxoProvider, never()).coversInstrument(any());
    }

    @Test
    void cryptoSymbolKeepsOriginalOrder_coverageNeverConsulted() {
        // Only the US provider feature-matches CRYPTO; saxo is filtered out by features. The crypto
        // symbol is non-European so no reorder/coverage probing happens at all.
        DataHorizonProbeService probe = new DataHorizonProbeService(
                List.of(usProvider, saxoProvider), rateLimitTracker);
        hasDataAlways(usProvider, "alpaca", "BTC");

        Optional<LocalDate> earliest = probe.probeEarliest(BTC, EventType.PRICE);

        assertThat(earliest).isPresent();
        verify(usProvider, atLeastOnce()).fetchDataInRange(any(), any(), any(), any(), any(Granularity.class));
        verify(usProvider, never()).coversInstrument(any());
        verify(saxoProvider, never()).coversInstrument(any());
    }

    @Test
    void euSymbol_withNoVenueNativeProvider_keepsFeatureMatchedOrder() {
        // No venue-native (EU_INSTRUMENT) provider present — only the US-default, which lacks
        // EU_INSTRUMENT. Single-element list short-circuits the partition; the provider is probed
        // unchanged and the result is still produced.
        DataHorizonProbeService probe = new DataHorizonProbeService(
                List.of(usProvider), rateLimitTracker);
        hasDataAlways(usProvider, "alpaca", "RHM");

        Optional<LocalDate> earliest = probe.probeEarliest(RHM_XETR, EventType.PRICE);

        assertThat(earliest).isPresent();
        verify(usProvider, atLeastOnce()).fetchDataInRange(any(), any(), any(), any(), any(Granularity.class));
    }
}
