package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.DividendInfoPort;
import com.quorumtrading.application.usecase.query.GetDividendInfoQuery;
import com.quorumtrading.model.valueobject.DividendInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetDividendInfoServiceTest {

    @Mock
    private DividendInfoPort dividendInfoPort;

    private GetDividendInfoService service;

    @BeforeEach
    void setUp() {
        service = new GetDividendInfoService(dividendInfoPort);
    }

    @Test
    void stockTickers_shouldBeResolvedViaPort() {
        Map<String, DividendInfo> portResult = new HashMap<>();
        portResult.put("AAPL", DividendInfo.withDividends("AAPL", new BigDecimal("0.96"), 4));
        portResult.put("MSFT", DividendInfo.withDividends("MSFT", new BigDecimal("3.00"), 4));

        when(dividendInfoPort.getDividendInfo(eq(Set.of("AAPL", "MSFT")), eq(Set.of())))
            .thenReturn(portResult);

        Map<String, DividendInfo> result = service.execute(new GetDividendInfoQuery(List.of("AAPL", "MSFT")));

        assertThat(result).hasSize(2);
        assertThat(result.get("AAPL").paysDividends()).isTrue();
        assertThat(result.get("AAPL").annualYieldEstimate()).isEqualByComparingTo("0.96");
        assertThat(result.get("MSFT").paysDividends()).isTrue();
        verify(dividendInfoPort).getDividendInfo(eq(Set.of("AAPL", "MSFT")), eq(Set.of()));
    }

    @Test
    void cryptoTickers_shouldBeClassifiedAsCrypto() {
        Map<String, DividendInfo> portResult = new HashMap<>();
        portResult.put("BTC", DividendInfo.crypto("BTC"));
        portResult.put("ETH", DividendInfo.crypto("ETH"));

        when(dividendInfoPort.getDividendInfo(eq(Set.of()), eq(Set.of("BTC", "ETH"))))
            .thenReturn(portResult);

        Map<String, DividendInfo> result = service.execute(new GetDividendInfoQuery(List.of("BTC", "ETH")));

        assertThat(result).hasSize(2);
        assertThat(result.get("BTC").assetType()).isEqualTo("CRYPTO");
        assertThat(result.get("BTC").paysDividends()).isFalse();
        assertThat(result.get("ETH").assetType()).isEqualTo("CRYPTO");
        // Port is still called — the service delegates separation to the port
        verify(dividendInfoPort).getDividendInfo(eq(Set.of()), eq(Set.of("BTC", "ETH")));
    }

    @Test
    void mixedStockAndCrypto_shouldSeparateCorrectly() {
        Map<String, DividendInfo> portResult = new HashMap<>();
        portResult.put("AAPL", DividendInfo.withDividends("AAPL", new BigDecimal("0.96"), 4));
        portResult.put("BTC", DividendInfo.crypto("BTC"));
        portResult.put("SOL", DividendInfo.crypto("SOL"));

        when(dividendInfoPort.getDividendInfo(eq(Set.of("AAPL")), eq(Set.of("BTC", "SOL"))))
            .thenReturn(portResult);

        Map<String, DividendInfo> result = service.execute(
            new GetDividendInfoQuery(List.of("AAPL", "BTC", "SOL")));

        assertThat(result).hasSize(3);
        assertThat(result.get("AAPL").assetType()).isEqualTo("STOCK");
        assertThat(result.get("BTC").assetType()).isEqualTo("CRYPTO");
        assertThat(result.get("SOL").assetType()).isEqualTo("CRYPTO");
        verify(dividendInfoPort).getDividendInfo(eq(Set.of("AAPL")), eq(Set.of("BTC", "SOL")));
    }

    @Test
    void emptyPortResponse_shouldReturnWhatPortProvides() {
        Map<String, DividendInfo> portResult = new HashMap<>();
        portResult.put("AAPL", DividendInfo.noDividends("AAPL"));
        portResult.put("TSLA", DividendInfo.noDividends("TSLA"));

        when(dividendInfoPort.getDividendInfo(any(), any())).thenReturn(portResult);

        Map<String, DividendInfo> result = service.execute(
            new GetDividendInfoQuery(List.of("AAPL", "TSLA")));

        assertThat(result).hasSize(2);
        assertThat(result.get("AAPL").paysDividends()).isFalse();
        assertThat(result.get("AAPL").assetType()).isEqualTo("STOCK");
        assertThat(result.get("TSLA").paysDividends()).isFalse();
    }

    @Test
    void portThrowingException_shouldReturnUnknownForAllTickers() {
        when(dividendInfoPort.getDividendInfo(any(), any()))
            .thenThrow(new RuntimeException("API failure"));

        Map<String, DividendInfo> result = service.execute(
            new GetDividendInfoQuery(List.of("AAPL", "BTC")));

        assertThat(result).hasSize(2);
        // The lookup failed, so nothing about AAPL's dividends was established. This
        // previously asserted false — turning an API outage into a confident claim
        // that the stock pays nothing.
        assertThat(result.get("AAPL").paysDividends()).isNull();
        assertThat(result.get("AAPL").isKnown()).isFalse();
        assertThat(result.get("AAPL").assetType()).isEqualTo("STOCK");
        // Crypto needs no lookup: a coin genuinely pays no dividends, so this stays
        // a known false rather than becoming unknown alongside it.
        assertThat(result.get("BTC").paysDividends()).isFalse();
        assertThat(result.get("BTC").isKnown()).isTrue();
        assertThat(result.get("BTC").assetType()).isEqualTo("CRYPTO");
    }
}
