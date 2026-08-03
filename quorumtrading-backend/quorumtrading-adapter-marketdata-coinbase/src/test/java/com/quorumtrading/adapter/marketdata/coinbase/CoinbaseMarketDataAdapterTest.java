package com.quorumtrading.adapter.marketdata.coinbase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.quorumtrading.adapter.marketdata.coinbase.client.CoinbaseRestClient;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.Granularity;
import com.quorumtrading.model.marketdata.MarketEvent;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure-Mockito unit tests — no CDI boot, no DB. The key risk here is Coinbase's
 * [time, low, high, open, close, volume] field ordering (low/high before open/close).
 */
class CoinbaseMarketDataAdapterTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-01-05T00:00:00Z");
    private static final long T02 = Instant.parse("2026-01-02T00:00:00Z").getEpochSecond();

    private CoinbaseMarketDataAdapter adapter;
    private CoinbaseRestClient client;

    @BeforeEach
    void setUp() {
        adapter = new CoinbaseMarketDataAdapter();
        client = mock(CoinbaseRestClient.class);
        adapter.client = client;
        adapter.enabled = true;
    }

    @Test
    void mapsCandlesWithCorrectFieldOrder() {
        // Coinbase row order is [time, low, high, open, close, volume].
        when(client.getCandles(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn("[[" + T02 + ", 95.0, 110.0, 100.5, 105.0, 12.5]]");

        List<MarketEvent> events = adapter.fetchDataInRange(
                Set.of("ETH"), Set.of(), START, END, Granularity.ONE_DAY);

        assertThat(events).hasSize(1);
        MarketEvent e = events.get(0);
        assertThat(e.eventType()).isEqualTo(EventType.PRICE);
        assertThat(e.source()).isEqualTo("coinbase");
        assertThat(e.symbol()).isEqualTo("ETH");
        assertThat(e.rawData().get("open")).isEqualTo(100.5);
        assertThat(e.rawData().get("high")).isEqualTo(110.0);
        assertThat(e.rawData().get("low")).isEqualTo(95.0);
        assertThat(e.rawData().get("close")).isEqualTo(105.0);
        assertThat(e.rawData().get("price")).isEqualTo(105.0);
        assertThat(e.rawData().get("volume")).isEqualTo(12.5);
        assertThat(e.rawData().get("symbol")).isEqualTo("ETH/USD");

        verify(client).getCandles(eq("ETH-USD"), eq(86400), anyString(), anyString());
    }

    @Test
    void errorObjectReturnsEmpty() {
        when(client.getCandles(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn("{\"message\":\"NotFound\"}");
        assertThat(adapter.fetchDataInRange(Set.of("BTC"), Set.of(), START, END, Granularity.ONE_DAY)).isEmpty();
    }

    @Test
    void unsupportedGranularityReturnsEmptyWithoutCalling() {
        // Coinbase has no 4-hour candle → adapter yields to another provider, no HTTP call.
        assertThat(adapter.fetchDataInRange(Set.of("BTC"), Set.of(), START, END, Granularity.FOUR_HOURS)).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void disabledReturnsEmpty() {
        adapter.enabled = false;
        assertThat(adapter.fetchDataInRange(Set.of("BTC"), Set.of(), START, END, Granularity.ONE_DAY)).isEmpty();
        verifyNoInteractions(client);
    }
}
