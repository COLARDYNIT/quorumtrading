package com.quorumtrading.adapter.marketdata.kraken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.quorumtrading.adapter.marketdata.kraken.client.KrakenRestClient;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.marketdata.MarketEvent;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pure-Mockito unit tests — no CDI boot, no DB. Exercise Kraken's string-valued OHLC parsing. */
class KrakenMarketDataAdapterTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-01-05T00:00:00Z");
    private static final long T02 = Instant.parse("2026-01-02T00:00:00Z").getEpochSecond();
    private static final long T03 = Instant.parse("2026-01-03T00:00:00Z").getEpochSecond();

    private KrakenMarketDataAdapter adapter;
    private KrakenRestClient client;

    @BeforeEach
    void setUp() {
        adapter = new KrakenMarketDataAdapter();
        client = mock(KrakenRestClient.class);
        adapter.client = client;
        adapter.enabled = true;
    }

    @Test
    void mapsOhlcvAndUsesXbtPairForBtc() {
        when(client.getOhlc(anyString(), anyInt(), anyLong())).thenReturn(
                "{\"error\":[],\"result\":{\"XXBTZUSD\":["
                        + "[" + T02 + ",\"100.5\",\"110.0\",\"95.0\",\"105.0\",\"103.0\",\"12.5\",50],"
                        + "[" + T03 + ",\"105.0\",\"120.0\",\"104.0\",\"118.0\",\"110.0\",\"20.0\",80]"
                        + "],\"last\":" + T03 + "}}");

        List<MarketEvent> events = adapter.fetchDataInRange(
                Set.of("BTC"), Set.of(), START, END, Granularity.ONE_DAY);

        assertThat(events).hasSize(2);
        MarketEvent first = events.get(0);
        assertThat(first.eventType()).isEqualTo(EventType.PRICE);
        assertThat(first.source()).isEqualTo("kraken");
        assertThat(first.symbol()).isEqualTo("BTC");
        assertThat(first.timestamp().getEpochSecond()).isEqualTo(T02);
        assertThat(first.rawData().get("open")).isEqualTo(100.5);
        assertThat(first.rawData().get("high")).isEqualTo(110.0);
        assertThat(first.rawData().get("low")).isEqualTo(95.0);
        assertThat(first.rawData().get("close")).isEqualTo(105.0);
        assertThat(first.rawData().get("price")).isEqualTo(105.0);
        assertThat(first.rawData().get("volume")).isEqualTo(12.5);
        assertThat(first.rawData().get("symbol")).isEqualTo("BTC/USD");

        verify(client).getOhlc(eq("XBTUSD"), eq(1440), anyLong());
    }

    @Test
    void krakenErrorReturnsEmpty() {
        when(client.getOhlc(anyString(), anyInt(), anyLong()))
                .thenReturn("{\"error\":[\"EQuery:Unknown asset pair\"],\"result\":{}}");
        assertThat(adapter.fetchDataInRange(Set.of("BTC"), Set.of(), START, END, Granularity.ONE_DAY)).isEmpty();
    }

    @Test
    void unsupportedGranularityReturnsEmptyWithoutCalling() {
        // Kraken has no monthly interval → adapter yields to another provider, no HTTP call.
        assertThat(adapter.fetchDataInRange(Set.of("BTC"), Set.of(), START, END, Granularity.ONE_MONTH)).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void disabledReturnsEmpty() {
        adapter.enabled = false;
        assertThat(adapter.fetchDataInRange(Set.of("BTC"), Set.of(), START, END, Granularity.ONE_DAY)).isEmpty();
        verifyNoInteractions(client);
    }
}
