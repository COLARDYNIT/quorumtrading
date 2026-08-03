package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.HistoricalPricePort;
import com.quorumtrading.application.usecase.BackFillTickerPriceDataUseCase;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.OverlayResult;
import com.quorumtrading.model.valueobject.PriceBar;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BuyAndHoldOverlayCalculatorTest {

    @Mock
    private HistoricalPricePort historicalPricePort;

    @Mock
    private BackFillTickerPriceDataUseCase backFillTickerPriceDataUseCase;

    private BuyAndHoldOverlayCalculator calculator;

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2024-06-30T23:59:59Z");
    private static final BigDecimal CAPITAL = new BigDecimal("100000");

    @BeforeEach
    void setUp() {
        calculator = new BuyAndHoldOverlayCalculator(historicalPricePort, backFillTickerPriceDataUseCase);
    }

    @Test
    void shouldComputeEquityCurve() {
        Symbol spy = new Symbol("SPY", InvestmentType.STOCK);
        List<PriceBar> bars = List.of(
            new PriceBar(START, null, null, null, new BigDecimal("400.00"), null),
            new PriceBar(START.plus(30, ChronoUnit.DAYS), null, null, null, new BigDecimal("420.00"), null),
            new PriceBar(END, null, null, null, new BigDecimal("440.00"), null)
        );
        when(historicalPricePort.getHistoricalPrices(eq(spy), eq(START), eq(END), eq("1d"))).thenReturn(bars);

        OverlayResult result = calculator.computeOverlay(spy, START, END, CAPITAL, "1d");

        assertThat(result.status()).isEqualTo(OverlayResult.OverlayStatus.READY);
        assertThat(result.symbol()).isEqualTo("SPY");
        assertThat(result.assetType()).isEqualTo("STOCK");
        assertThat(result.dataPoints()).hasSize(3);
        assertThat(result.dataPoints().get(0).portfolioValue()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(result.dataPoints().get(2).portfolioValue()).isEqualByComparingTo(new BigDecimal("110000.00"));
        assertThat(result.returnPercent()).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    @Test
    void shouldReturnNoDataWhenNoBarsExist() {
        Symbol unknown = new Symbol("XYZ", InvestmentType.STOCK);
        when(historicalPricePort.getHistoricalPrices(eq(unknown), eq(START), eq(END), eq("1d")))
            .thenReturn(List.of());
        when(backFillTickerPriceDataUseCase.execute(any())).thenThrow(new RuntimeException("No data"));

        OverlayResult result = calculator.computeOverlay(unknown, START, END, CAPITAL, "1d");

        assertThat(result.status()).isEqualTo(OverlayResult.OverlayStatus.NO_DATA);
        assertThat(result.errorMessage()).contains("XYZ");
    }

    @Test
    void shouldAutoBackfillWhenGapExceedsThreshold() {
        Symbol spy = new Symbol("SPY", InvestmentType.STOCK);
        when(historicalPricePort.getHistoricalPrices(eq(spy), eq(START), eq(END), eq("1d")))
            .thenReturn(List.of())
            .thenReturn(List.of(
                new PriceBar(START, null, null, null, new BigDecimal("400.00"), null),
                new PriceBar(END, null, null, null, new BigDecimal("400.00"), null)
            ));

        OverlayResult result = calculator.computeOverlay(spy, START, END, CAPITAL, "1d");

        verify(backFillTickerPriceDataUseCase).execute(any());
        assertThat(result.status()).isEqualTo(OverlayResult.OverlayStatus.READY);
    }

    @Test
    void shouldNotBackfillWhenGapBelowThreshold() {
        Symbol spy = new Symbol("SPY", InvestmentType.STOCK);
        Instant slightlyLate = START.plus(3, ChronoUnit.DAYS);
        when(historicalPricePort.getHistoricalPrices(eq(spy), eq(START), eq(END), eq("1d")))
            .thenReturn(List.of(
                new PriceBar(slightlyLate, null, null, null, new BigDecimal("400.00"), null),
                new PriceBar(END, null, null, null, new BigDecimal("440.00"), null)
            ));

        OverlayResult result = calculator.computeOverlay(spy, START, END, CAPITAL, "1d");

        verifyNoInteractions(backFillTickerPriceDataUseCase);
        assertThat(result.status()).isEqualTo(OverlayResult.OverlayStatus.READY);
    }

    @Test
    void shouldHandleCryptoAssetType() {
        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        when(historicalPricePort.getHistoricalPrices(eq(btc), eq(START), eq(END), eq("1d")))
            .thenReturn(List.of(
                new PriceBar(START, null, null, null, new BigDecimal("40000.00"), null),
                new PriceBar(END, null, null, null, new BigDecimal("60000.00"), null)
            ));

        OverlayResult result = calculator.computeOverlay(btc, START, END, CAPITAL, "1d");

        assertThat(result.assetType()).isEqualTo("CRYPTO");
        assertThat(result.returnPercent()).isEqualByComparingTo(new BigDecimal("50.0000"));
    }
}
