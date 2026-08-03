package com.quorumtrading.adapter.marketdata.tiingo;

import com.quorumtrading.model.marketdata.MarketEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The adapter ships {@code enabled=true} by default while the API token defaults to the
 * {@code not-configured} sentinel. Without a token-aware guard a keyless install called Tiingo
 * anyway and surfaced "Authentication failed for tiingo (HTTP 403)" — which reads as a broken key
 * to a user who never configured one. These tests pin the unconfigured behaviour.
 */
class TiingoMarketDataAdapterConfigTest {

    private static final Instant START = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant END = START.plus(30, ChronoUnit.DAYS);

    @Test
    @DisplayName("The 'not-configured' placeholder does not count as a real token")
    void placeholderTokenIsNotConfigured() {
        TiingoMarketDataAdapter adapter = adapter(true, TiingoMarketDataAdapter.TOKEN_NOT_CONFIGURED);

        assertThat(adapter.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("A blank or null token does not count as a real token")
    void blankTokenIsNotConfigured() {
        assertThat(adapter(true, "").isConfigured()).isFalse();
        assertThat(adapter(true, "   ").isConfigured()).isFalse();
        assertThat(adapter(true, null).isConfigured()).isFalse();
    }

    @Test
    @DisplayName("A real token counts as configured")
    void realTokenIsConfigured() {
        assertThat(adapter(true, "a-real-tiingo-token").isConfigured()).isTrue();
    }

    /**
     * Switched-on and credentialed are different facts, and until an adapter could be asked the
     * first one, capability discovery handed out providers that were deliberately off — which then
     * got called, and got named in the failure message as though they had been tried.
     */
    @Test
    @DisplayName("Enabled is reported separately from configured")
    void enabledIsDistinctFromConfigured() {
        assertThat(adapter(false, "a-real-tiingo-token").isEnabled()).isFalse();
        assertThat(adapter(false, "a-real-tiingo-token").isConfigured()).isTrue();
        assertThat(adapter(true, "a-real-tiingo-token").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Enabled but unconfigured → returns empty instead of calling Tiingo (no 403)")
    void enabledButUnconfigured_returnsEmptyWithoutCallingApi() {
        // tiingoClient is left null on purpose: if the guard fails to short-circuit, the fetch
        // dereferences it and this test fails with an NPE rather than silently passing.
        TiingoMarketDataAdapter adapter = adapter(true, TiingoMarketDataAdapter.TOKEN_NOT_CONFIGURED);

        List<MarketEvent> events =
            adapter.fetchDataInRange(Set.of(), Set.of("NVDA"), START, END);

        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("Disabled → returns empty regardless of token")
    void disabled_returnsEmpty() {
        TiingoMarketDataAdapter adapter = adapter(false, "a-real-tiingo-token");

        List<MarketEvent> events =
            adapter.fetchDataInRange(Set.of(), Set.of("NVDA"), START, END);

        assertThat(events).isEmpty();
    }

    private static TiingoMarketDataAdapter adapter(boolean enabled, String token) {
        TiingoMarketDataAdapter adapter = new TiingoMarketDataAdapter();
        adapter.enabled = enabled;
        adapter.apiToken = token;
        return adapter;
    }
}
