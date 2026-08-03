package com.quorumtrading.adapter.marketdata.alpaca;

import com.quorumtrading.adapter.marketdata.alpaca.client.AlpacaMarketDataClient;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.TickerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Polling entry points must skip quietly (WARN, empty result) when credentials are absent or
 * still the 'not-configured' placeholder — a clean zero-key boot must not call Alpaca or log
 * ERRORs.
 */
@ExtendWith(MockitoExtension.class)
class AlpacaMarketDataAdapterCredentialsTest {

    @Mock
    private AlpacaMarketDataClient marketDataClient;

    @InjectMocks
    private AlpacaMarketDataAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        setField("enabled", true);
        setField("stockFeed", "iex");
        setField("configuredCryptoSymbols", "BTC/USD");
        setField("configuredStockSymbols", "AAPL");
        setField("apiKey", "not-configured");
        setField("apiSecret", "not-configured");
    }

    @Test
    void fetchLatestDataForTickers_withPlaceholderCredentials_skipsWithoutCallingAlpaca() {
        List<MarketEvent> events = adapter.fetchLatestDataForTickers(TickerRegistry.empty());

        assertTrue(events.isEmpty());
        verifyNoInteractions(marketDataClient);
    }

    @Test
    @SuppressWarnings("deprecation")
    void fetchLatestData_withPlaceholderCredentials_skipsWithoutCallingAlpaca() {
        List<MarketEvent> events = adapter.fetchLatestData();

        assertTrue(events.isEmpty());
        verifyNoInteractions(marketDataClient);
    }

    @Test
    void fetchLatestDataForTickers_withBlankCredentials_skipsWithoutCallingAlpaca() throws Exception {
        setField("apiKey", "");
        setField("apiSecret", "");

        List<MarketEvent> events = adapter.fetchLatestDataForTickers(TickerRegistry.empty());

        assertTrue(events.isEmpty());
        verifyNoInteractions(marketDataClient);
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = AlpacaMarketDataAdapter.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(adapter, value);
    }
}
