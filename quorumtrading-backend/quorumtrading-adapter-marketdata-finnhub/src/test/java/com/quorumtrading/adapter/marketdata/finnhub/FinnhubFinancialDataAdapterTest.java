package com.quorumtrading.adapter.marketdata.finnhub;

import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The credential guard is what a fresh keyless install exercises on every data-collection
 * pass — it must skip without ever calling Finnhub. (The happy path lives in the
 * env-gated {@link FinnhubStockAdapterIntegrationTest}, which CI never runs; this guard
 * must not depend on it for coverage.)
 */
class FinnhubFinancialDataAdapterTest {

    private FinnhubRestClient restClient;
    private FinnhubFinancialDataAdapter adapter;

    @BeforeEach
    void setUp() {
        restClient = Mockito.mock(FinnhubRestClient.class);
        adapter = new FinnhubFinancialDataAdapter();
        adapter.restClient = restClient;
    }

    @Test
    void shouldSkipWithoutCallingFinnhubWhenKeyIsTheNotConfiguredPlaceholder() {
        adapter.apiKey = "not-configured";

        assertTrue(adapter.fetchFinancialMetrics(new Symbol("AAPL", InvestmentType.STOCK)).isEmpty());
        verifyNoInteractions(restClient);
    }

    @Test
    void shouldSkipWithoutCallingFinnhubWhenKeyIsBlank() {
        adapter.apiKey = "";

        assertTrue(adapter.fetchFinancialMetrics(new Symbol("AAPL", InvestmentType.STOCK)).isEmpty());
        verifyNoInteractions(restClient);
    }

    @Test
    void shouldSkipCryptoSymbolsEvenWithAKey() {
        adapter.apiKey = "real-looking-key";

        assertTrue(adapter.fetchFinancialMetrics(new Symbol("BTC", InvestmentType.CRYPTO)).isEmpty());
        verifyNoInteractions(restClient);
    }
}
