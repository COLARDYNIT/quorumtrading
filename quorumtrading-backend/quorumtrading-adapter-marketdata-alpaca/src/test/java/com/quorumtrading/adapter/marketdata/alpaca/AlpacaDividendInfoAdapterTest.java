package com.quorumtrading.adapter.marketdata.alpaca;

import com.quorumtrading.adapter.marketdata.alpaca.client.AlpacaMarketDataClient;
import com.quorumtrading.adapter.marketdata.alpaca.client.dto.CorporateActionsResponse;
import com.quorumtrading.adapter.marketdata.alpaca.client.dto.CorporateActionsResponse.CashDividend;
import com.quorumtrading.adapter.marketdata.alpaca.client.dto.CorporateActionsResponse.CorporateActions;
import com.quorumtrading.model.valueobject.DividendInfo;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlpacaDividendInfoAdapterTest {

    @Mock
    private AlpacaMarketDataClient marketDataClient;

    @InjectMocks
    private AlpacaDividendInfoAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        // @InjectMocks injects marketDataClient but not the @ConfigProperty Strings. Set real
        // credentials so the corporate-actions path runs (it now gates on credentialsConfigured()).
        setField("apiKey", "test-key");
        setField("apiSecret", "test-secret");
    }

    private void setField(String name, Object value) throws Exception {
        Field f = AlpacaDividendInfoAdapter.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(adapter, value);
    }

    @Test
    void shouldSkipCorporateActionsWhenCredentialsNotConfigured() throws Exception {
        // Zero-key standalone: skip the Alpaca call cleanly (no 401) instead of erroring.
        setField("apiKey", "not-configured");
        setField("apiSecret", "not-configured");

        Map<String, DividendInfo> result = adapter.getDividendInfo(Set.of("AAPL"), Set.of());

        assertThat(result).doesNotContainKey("AAPL");
        verifyNoInteractions(marketDataClient);
    }

    @Test
    void shouldReturnDividendInfo_forStockTickers() {
        CorporateActionsResponse response = buildResponse(
            List.of(
                buildCashDividend("AAPL", new BigDecimal("0.24")),
                buildCashDividend("AAPL", new BigDecimal("0.24")),
                buildCashDividend("AAPL", new BigDecimal("0.24")),
                buildCashDividend("AAPL", new BigDecimal("0.24")),
                buildCashDividend("MSFT", new BigDecimal("0.75")),
                buildCashDividend("MSFT", new BigDecimal("0.75"))
            ),
            null // no next page
        );

        when(marketDataClient.getCorporateActions(anyString(), anyString(), anyString(), anyString(), isNull()))
            .thenReturn(response);

        Map<String, DividendInfo> result = adapter.getDividendInfo(
            Set.of("AAPL", "MSFT"), Set.of());

        assertThat(result).hasSize(2);

        DividendInfo aapl = result.get("AAPL");
        assertThat(aapl.paysDividends()).isTrue();
        assertThat(aapl.dividendCountLastYear()).isEqualTo(4);
        assertThat(aapl.annualYieldEstimate()).isEqualByComparingTo("0.96"); // 4 x 0.24

        DividendInfo msft = result.get("MSFT");
        assertThat(msft.paysDividends()).isTrue();
        assertThat(msft.dividendCountLastYear()).isEqualTo(2);
        assertThat(msft.annualYieldEstimate()).isEqualByComparingTo("1.50"); // 2 x 0.75
    }

    @Test
    void shouldReturnCryptoWithoutApiCall() {
        Map<String, DividendInfo> result = adapter.getDividendInfo(
            Set.of(), Set.of("BTC", "ETH"));

        assertThat(result).hasSize(2);
        assertThat(result.get("BTC").assetType()).isEqualTo("CRYPTO");
        assertThat(result.get("BTC").paysDividends()).isFalse();
        assertThat(result.get("ETH").assetType()).isEqualTo("CRYPTO");
        assertThat(result.get("ETH").paysDividends()).isFalse();

        // Verify no API call was made for crypto-only request
        verifyNoInteractions(marketDataClient);
    }

    @Test
    void shouldReturnNoDividends_whenCorporateActionsResponseIsEmpty() {
        CorporateActionsResponse response = buildResponse(List.of(), null);

        when(marketDataClient.getCorporateActions(anyString(), anyString(), anyString(), anyString(), isNull()))
            .thenReturn(response);

        Map<String, DividendInfo> result = adapter.getDividendInfo(
            Set.of("TSLA"), Set.of());

        assertThat(result).hasSize(1);
        DividendInfo tsla = result.get("TSLA");
        assertThat(tsla.paysDividends()).isFalse();
        assertThat(tsla.dividendCountLastYear()).isZero();
        assertThat(tsla.annualYieldEstimate()).isNull();
        assertThat(tsla.assetType()).isEqualTo("STOCK");
    }

    @Test
    void shouldReturnUnknown_whenApiThrowsException() {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(500);
        WebApplicationException exception = new WebApplicationException("Server error", mockResponse);

        when(marketDataClient.getCorporateActions(anyString(), anyString(), anyString(), anyString(), isNull()))
            .thenThrow(exception);

        Map<String, DividendInfo> result = adapter.getDividendInfo(
            Set.of("AAPL"), Set.of());

        // Graceful degradation: returns unknown for failed tickers. Unknown means
        // null, not false — the API failed, so nothing about AAPL's dividends was
        // learned, and reporting "pays none" would state the opposite of the truth
        // for a stock that in fact pays them.
        assertThat(result).hasSize(1);
        DividendInfo aapl = result.get("AAPL");
        assertThat(aapl.paysDividends()).isNull();
        assertThat(aapl.isKnown()).isFalse();
        assertThat(aapl.assetType()).isEqualTo("STOCK");
    }

    @Test
    void shouldUseCacheOnSecondCall() {
        CorporateActionsResponse response = buildResponse(
            List.of(buildCashDividend("AAPL", new BigDecimal("0.24"))),
            null
        );

        when(marketDataClient.getCorporateActions(anyString(), anyString(), anyString(), anyString(), isNull()))
            .thenReturn(response);

        // First call — hits the API
        Map<String, DividendInfo> firstResult = adapter.getDividendInfo(
            Set.of("AAPL"), Set.of());

        assertThat(firstResult.get("AAPL").paysDividends()).isTrue();

        // Second call — should use cache, not call API again
        Map<String, DividendInfo> secondResult = adapter.getDividendInfo(
            Set.of("AAPL"), Set.of());

        assertThat(secondResult.get("AAPL").paysDividends()).isTrue();

        // API should have been called only once
        verify(marketDataClient, times(1))
            .getCorporateActions(anyString(), anyString(), anyString(), anyString(), isNull());
    }

    // --- Helper methods ---

    private CashDividend buildCashDividend(String symbol, BigDecimal rate) {
        CashDividend dividend = new CashDividend();
        dividend.setSymbol(symbol);
        dividend.setRate(rate);
        dividend.setExDate("2025-06-01");
        return dividend;
    }

    private CorporateActionsResponse buildResponse(List<CashDividend> dividends, String nextPageToken) {
        CorporateActions actions = new CorporateActions();
        actions.setCashDividends(dividends);

        CorporateActionsResponse response = new CorporateActionsResponse();
        response.setCorporateActions(actions);
        response.setNextPageToken(nextPageToken);
        return response;
    }
}
