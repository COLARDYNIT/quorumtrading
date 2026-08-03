package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.HistoricalPricePort;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.IndicatorContext;
import com.quorumtrading.model.valueobject.IndicatorSnapshot;
import com.quorumtrading.model.valueobject.IndicatorType;
import com.quorumtrading.model.valueobject.IndicatorValue;
import com.quorumtrading.model.valueobject.PriceBar;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IndicatorEnrichmentService.
 *
 * <p><b>Testing Strategy:</b></p>
 * <ul>
 *   <li>Tests use mocked HistoricalPricePort to control price data</li>
 *   <li>Real IndicatorCalculationService is used for calculation accuracy</li>
 *   <li>Tests verify indicator values, warnings, and error handling</li>
 * </ul>
 */
class IndicatorEnrichmentServiceTest {

    private HistoricalPricePort historicalPricePort;
    private IndicatorCalculationService calculationService;
    private IndicatorEnrichmentService enrichmentService;

    private static final Symbol AAPL = new Symbol("AAPL", InvestmentType.STOCK);
    private static final Symbol NVDA = new Symbol("NVDA", InvestmentType.STOCK);

    @BeforeEach
    void setUp() {
        historicalPricePort = mock(HistoricalPricePort.class);
        calculationService = new IndicatorCalculationService();
        enrichmentService = new IndicatorEnrichmentService(historicalPricePort, calculationService);
    }

    @Test
    @DisplayName("Empty ticker list returns empty context")
    void emptyTickerListReturnsEmptyContext() {
        // When
        IndicatorContext context = enrichmentService.calculateIndicators(Collections.emptyList());

        // Then
        assertNotNull(context);
        assertTrue(context.tickerIndicators().isEmpty());
        verifyNoInteractions(historicalPricePort);
    }

    @Test
    @DisplayName("All indicators calculated when sufficient data (200+ days)")
    void allIndicatorsCalculatedWithSufficientData() {
        // Given: 250 days of price data (enough for all indicators)
        List<PriceBar> prices = generatePriceBars(250, new BigDecimal("150.00"), new BigDecimal("0.50"));
        when(historicalPricePort.getHistoricalPrices(eq(AAPL), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), any(Instant.class))).thenReturn(prices);

        // When
        IndicatorContext context = enrichmentService.calculateIndicators(List.of(AAPL));

        // Then
        assertNotNull(context);
        assertEquals(1, context.tickerIndicators().size());

        IndicatorSnapshot snapshot = context.tickerIndicators().get(AAPL);
        assertNotNull(snapshot);
        assertEquals(AAPL, snapshot.ticker());

        // All indicator types should be present
        assertTrue(snapshot.values().containsKey(IndicatorType.MOVING_AVERAGE));
        assertTrue(snapshot.values().containsKey(IndicatorType.RSI));
        assertTrue(snapshot.values().containsKey(IndicatorType.MACD));
        assertTrue(snapshot.values().containsKey(IndicatorType.BOLLINGER_BANDS));

        // No warnings when all data is sufficient
        assertTrue(snapshot.warnings().isEmpty(),
            "Expected no warnings but got: " + snapshot.warnings());

        // Verify specific indicator values are correct types
        assertInstanceOf(IndicatorValue.MovingAverageValue.class,
            snapshot.values().get(IndicatorType.MOVING_AVERAGE));
        assertInstanceOf(IndicatorValue.RSIValue.class,
            snapshot.values().get(IndicatorType.RSI));
        assertInstanceOf(IndicatorValue.MACDValue.class,
            snapshot.values().get(IndicatorType.MACD));
        assertInstanceOf(IndicatorValue.BollingerValue.class,
            snapshot.values().get(IndicatorType.BOLLINGER_BANDS));

        // Verify moving averages include both SMA-50 and SMA-200
        IndicatorValue.MovingAverageValue ma =
            (IndicatorValue.MovingAverageValue) snapshot.values().get(IndicatorType.MOVING_AVERAGE);
        assertNotNull(ma.sma50(), "SMA-50 should be calculated");
        assertNotNull(ma.sma200(), "SMA-200 should be calculated with 250 days of data");
    }

    @Test
    @DisplayName("Warning added when insufficient data for SMA-200 (only 150 days)")
    void warningAddedWhenInsufficientDataForSMA200() {
        // Given: Only 150 days of price data (enough for SMA-50, not SMA-200)
        List<PriceBar> prices = generatePriceBars(150, new BigDecimal("100.00"), new BigDecimal("0.30"));
        when(historicalPricePort.getHistoricalPrices(eq(AAPL), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), any(Instant.class))).thenReturn(prices);

        // When
        IndicatorContext context = enrichmentService.calculateIndicators(List.of(AAPL));

        // Then
        assertNotNull(context);
        IndicatorSnapshot snapshot = context.tickerIndicators().get(AAPL);
        assertNotNull(snapshot);

        // SMA-50 should be calculated, SMA-200 should be null
        IndicatorValue.MovingAverageValue ma =
            (IndicatorValue.MovingAverageValue) snapshot.values().get(IndicatorType.MOVING_AVERAGE);
        assertNotNull(ma.sma50(), "SMA-50 should be calculated with 150 days");
        assertNull(ma.sma200(), "SMA-200 should be null with only 150 days");

        // Warning should be present for MOVING_AVERAGE
        assertTrue(snapshot.warnings().containsKey(IndicatorType.MOVING_AVERAGE),
            "Expected warning for MOVING_AVERAGE");
        String warning = snapshot.warnings().get(IndicatorType.MOVING_AVERAGE);
        assertTrue(warning.contains("SMA-200") || warning.contains("200"),
            "Warning should mention SMA-200 or 200-day requirement");

        // Other indicators should still be calculated (RSI, MACD, Bollinger)
        assertTrue(snapshot.values().containsKey(IndicatorType.RSI));
        assertTrue(snapshot.values().containsKey(IndicatorType.MACD));
        assertTrue(snapshot.values().containsKey(IndicatorType.BOLLINGER_BANDS));
    }

    @Test
    @DisplayName("Empty price history returns empty snapshot with warnings")
    void emptyPriceHistoryReturnsEmptySnapshotWithWarnings() {
        // Given: No price data available
        when(historicalPricePort.getHistoricalPrices(eq(AAPL), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), any(Instant.class))).thenReturn(Collections.emptyList());

        // When
        IndicatorContext context = enrichmentService.calculateIndicators(List.of(AAPL));

        // Then
        assertNotNull(context);
        IndicatorSnapshot snapshot = context.tickerIndicators().get(AAPL);
        assertNotNull(snapshot);

        // No indicator values should be calculated
        assertTrue(snapshot.values().isEmpty(),
            "Expected no values but got: " + snapshot.values());

        // All indicator types should have warnings
        assertEquals(IndicatorType.values().length, snapshot.warnings().size(),
            "All indicator types should have warnings");
        for (IndicatorType type : IndicatorType.values()) {
            assertTrue(snapshot.warnings().containsKey(type),
                "Warning should be present for " + type);
        }
    }

    @Test
    @DisplayName("Price port exception returns snapshot with warnings")
    void pricePortExceptionReturnsSnapshotWithWarnings() {
        // Given: Price port throws exception
        when(historicalPricePort.getHistoricalPrices(eq(AAPL), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), any(Instant.class)))
            .thenThrow(new RuntimeException("Database connection failed"));

        // When
        IndicatorContext context = enrichmentService.calculateIndicators(List.of(AAPL));

        // Then
        assertNotNull(context);
        IndicatorSnapshot snapshot = context.tickerIndicators().get(AAPL);
        assertNotNull(snapshot);

        // No indicator values should be calculated
        assertTrue(snapshot.values().isEmpty(),
            "Expected no values when exception occurs but got: " + snapshot.values());

        // All indicator types should have warnings mentioning the error
        assertFalse(snapshot.warnings().isEmpty(),
            "Expected warnings when exception occurs");
    }

    @Test
    @DisplayName("Multiple tickers calculated independently")
    void multipleTickersCalculatedIndependently() {
        // Given: Different data for each ticker
        List<PriceBar> aaplPrices = generatePriceBars(250, new BigDecimal("180.00"), new BigDecimal("1.00"));
        List<PriceBar> nvdaPrices = generatePriceBars(100, new BigDecimal("500.00"), new BigDecimal("2.00")); // Less data

        when(historicalPricePort.getHistoricalPrices(eq(AAPL), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), any(Instant.class))).thenReturn(aaplPrices);
        when(historicalPricePort.getHistoricalPrices(eq(NVDA), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), any(Instant.class))).thenReturn(nvdaPrices);

        // When
        IndicatorContext context = enrichmentService.calculateIndicators(List.of(AAPL, NVDA));

        // Then
        assertNotNull(context);
        assertEquals(2, context.tickerIndicators().size());

        // AAPL should have all indicators (250 days)
        IndicatorSnapshot aaplSnapshot = context.tickerIndicators().get(AAPL);
        assertNotNull(aaplSnapshot);
        assertTrue(aaplSnapshot.values().containsKey(IndicatorType.MOVING_AVERAGE));
        IndicatorValue.MovingAverageValue aaplMa =
            (IndicatorValue.MovingAverageValue) aaplSnapshot.values().get(IndicatorType.MOVING_AVERAGE);
        assertNotNull(aaplMa.sma200(), "AAPL should have SMA-200 with 250 days");

        // NVDA should have SMA-50 but not SMA-200 (100 days)
        IndicatorSnapshot nvdaSnapshot = context.tickerIndicators().get(NVDA);
        assertNotNull(nvdaSnapshot);
        assertTrue(nvdaSnapshot.values().containsKey(IndicatorType.MOVING_AVERAGE));
        IndicatorValue.MovingAverageValue nvdaMa =
            (IndicatorValue.MovingAverageValue) nvdaSnapshot.values().get(IndicatorType.MOVING_AVERAGE);
        assertNotNull(nvdaMa.sma50(), "NVDA should have SMA-50 with 100 days");
        assertNull(nvdaMa.sma200(), "NVDA should not have SMA-200 with only 100 days");

        // Verify independence: AAPL success shouldn't affect NVDA warnings
        assertTrue(nvdaSnapshot.warnings().containsKey(IndicatorType.MOVING_AVERAGE));
    }

    @Test
    @DisplayName("One ticker failure does not affect other tickers")
    void oneTickerFailureDoesNotAffectOthers() {
        // Given: AAPL works, NVDA throws exception
        List<PriceBar> aaplPrices = generatePriceBars(250, new BigDecimal("180.00"), new BigDecimal("1.00"));
        when(historicalPricePort.getHistoricalPrices(eq(AAPL), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), any(Instant.class))).thenReturn(aaplPrices);
        when(historicalPricePort.getHistoricalPrices(eq(NVDA), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), any(Instant.class)))
            .thenThrow(new RuntimeException("Network timeout"));

        // When
        IndicatorContext context = enrichmentService.calculateIndicators(List.of(AAPL, NVDA));

        // Then
        assertNotNull(context);
        assertEquals(2, context.tickerIndicators().size());

        // AAPL should have successful calculations
        IndicatorSnapshot aaplSnapshot = context.tickerIndicators().get(AAPL);
        assertFalse(aaplSnapshot.values().isEmpty(), "AAPL should have calculated values");

        // NVDA should have warnings but still be in the context
        IndicatorSnapshot nvdaSnapshot = context.tickerIndicators().get(NVDA);
        assertNotNull(nvdaSnapshot, "NVDA should be in context even with failure");
        assertTrue(nvdaSnapshot.values().isEmpty(), "NVDA should have no values due to exception");
        assertFalse(nvdaSnapshot.warnings().isEmpty(), "NVDA should have warnings");
    }

    @Test
    @DisplayName("RSI calculated correctly with sufficient data")
    void rsiCalculatedCorrectly() {
        // Given: 50 days of data (enough for RSI-14 which needs 15 days)
        List<PriceBar> prices = generatePriceBars(50, new BigDecimal("100.00"), new BigDecimal("0.50"));
        when(historicalPricePort.getHistoricalPrices(eq(AAPL), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), any(Instant.class))).thenReturn(prices);

        // When
        IndicatorContext context = enrichmentService.calculateIndicators(List.of(AAPL));

        // Then
        IndicatorSnapshot snapshot = context.tickerIndicators().get(AAPL);
        assertTrue(snapshot.values().containsKey(IndicatorType.RSI));

        IndicatorValue.RSIValue rsi =
            (IndicatorValue.RSIValue) snapshot.values().get(IndicatorType.RSI);
        assertNotNull(rsi.rsi14());
        assertTrue(rsi.rsi14().compareTo(BigDecimal.ZERO) >= 0, "RSI should be >= 0");
        assertTrue(rsi.rsi14().compareTo(new BigDecimal("100")) <= 0, "RSI should be <= 100");
    }

    @Test
    @DisplayName("MACD calculated correctly with sufficient data")
    void macdCalculatedCorrectly() {
        // Given: 50 days of data (enough for MACD which needs 35 days)
        List<PriceBar> prices = generatePriceBars(50, new BigDecimal("100.00"), new BigDecimal("0.50"));
        when(historicalPricePort.getHistoricalPrices(eq(AAPL), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), any(Instant.class))).thenReturn(prices);

        // When
        IndicatorContext context = enrichmentService.calculateIndicators(List.of(AAPL));

        // Then
        IndicatorSnapshot snapshot = context.tickerIndicators().get(AAPL);
        assertTrue(snapshot.values().containsKey(IndicatorType.MACD));

        IndicatorValue.MACDValue macd =
            (IndicatorValue.MACDValue) snapshot.values().get(IndicatorType.MACD);
        assertNotNull(macd.macdLine());
        assertNotNull(macd.signalLine());
        assertNotNull(macd.histogram());
    }

    @Test
    @DisplayName("Bollinger Bands calculated correctly with sufficient data")
    void bollingerBandsCalculatedCorrectly() {
        // Given: 50 days of data (enough for Bollinger which needs 20 days)
        List<PriceBar> prices = generatePriceBars(50, new BigDecimal("100.00"), new BigDecimal("0.50"));
        when(historicalPricePort.getHistoricalPrices(eq(AAPL), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), any(Instant.class))).thenReturn(prices);

        // When
        IndicatorContext context = enrichmentService.calculateIndicators(List.of(AAPL));

        // Then
        IndicatorSnapshot snapshot = context.tickerIndicators().get(AAPL);
        assertTrue(snapshot.values().containsKey(IndicatorType.BOLLINGER_BANDS));

        IndicatorValue.BollingerValue bb =
            (IndicatorValue.BollingerValue) snapshot.values().get(IndicatorType.BOLLINGER_BANDS);
        assertNotNull(bb.upper());
        assertNotNull(bb.middle());
        assertNotNull(bb.lower());

        // Verify band ordering: lower <= middle <= upper
        assertTrue(bb.lower().compareTo(bb.middle()) <= 0, "Lower should be <= middle");
        assertTrue(bb.middle().compareTo(bb.upper()) <= 0, "Middle should be <= upper");
    }

    @Test
    @DisplayName("asOf overload passes timestamp to price port")
    void asOfOverloadPassesTimestampToPricePort() {
        // Given: A specific historical timestamp (June 2023 backtest)
        Instant backtestDate = Instant.parse("2023-06-15T14:30:00Z");
        List<PriceBar> prices = generatePriceBars(250, new BigDecimal("150.00"), new BigDecimal("0.50"));
        when(historicalPricePort.getHistoricalPrices(eq(AAPL), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), eq(backtestDate)))
            .thenReturn(prices);

        // When: calculateIndicators with explicit asOf
        IndicatorContext context = enrichmentService.calculateIndicators(List.of(AAPL), backtestDate);

        // Then: Port was called with the exact backtest timestamp
        verify(historicalPricePort).getHistoricalPrices(eq(AAPL), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), eq(backtestDate));
        assertNotNull(context);
        IndicatorSnapshot snapshot = context.tickerIndicators().get(AAPL);
        assertNotNull(snapshot);

        // calculatedAt should be the backtest date, not Instant.now()
        assertEquals(backtestDate, snapshot.calculatedAt());

        // Indicators should be computed normally
        assertTrue(snapshot.values().containsKey(IndicatorType.MOVING_AVERAGE));
    }

    @Test
    @DisplayName("Different asOf dates produce different cache entries")
    void differentAsOfDatesProduceDifferentCacheEntries() {
        // Given: Two different backtest dates
        Instant day1 = Instant.parse("2023-06-15T14:30:00Z");
        Instant day2 = Instant.parse("2023-06-16T14:30:00Z");

        List<PriceBar> pricesDay1 = generatePriceBars(250, new BigDecimal("150.00"), new BigDecimal("0.50"));
        List<PriceBar> pricesDay2 = generatePriceBars(250, new BigDecimal("160.00"), new BigDecimal("0.50"));

        when(historicalPricePort.getHistoricalPrices(eq(AAPL), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), eq(day1))).thenReturn(pricesDay1);
        when(historicalPricePort.getHistoricalPrices(eq(AAPL), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), eq(day2))).thenReturn(pricesDay2);

        // When: Calculate for both days
        enrichmentService.calculateIndicators(List.of(AAPL), day1);
        enrichmentService.calculateIndicators(List.of(AAPL), day2);

        // Then: Both timestamps resulted in separate price fetches (not cached together)
        verify(historicalPricePort).getHistoricalPrices(eq(AAPL), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), eq(day1));
        verify(historicalPricePort).getHistoricalPrices(eq(AAPL), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), eq(day2));
    }

    /**
     * Two listings of one company are different instruments. The cache key was built from
     * {@code ticker()} — the bare root — so {@code SAP:XETR} and {@code SAP} collided on one
     * entry: whichever was asked for first populated it, and the second silently received the
     * other instrument's prices under its own name. {@link Symbol} already defines identity as
     * {@code canonical()}; the key has to use it.
     */
    @Test
    @DisplayName("A venue-qualified symbol does not share a cache entry with its US root")
    void venueQualifiedSymbolDoesNotCollideWithUsRoot() {
        Instant asOf = Instant.parse("2026-07-29T00:00:00Z");
        Symbol sapUs = new Symbol("SAP", InvestmentType.STOCK);
        Symbol sapXetr = Symbol.parse("SAP:XETR");

        List<PriceBar> usPrices = generatePriceBars(250, new BigDecimal("150.00"), new BigDecimal("0.50"));
        List<PriceBar> euPrices = generatePriceBars(250, new BigDecimal("250.00"), new BigDecimal("0.50"));
        when(historicalPricePort.getHistoricalPrices(eq(sapUs), anyInt(), eq(asOf))).thenReturn(usPrices);
        when(historicalPricePort.getHistoricalPrices(eq(sapXetr), anyInt(), eq(asOf))).thenReturn(euPrices);

        // Warm the cache with the US listing first — the order that produced the wrong answer.
        enrichmentService.calculateIndicators(List.of(sapUs), asOf);
        IndicatorContext euContext = enrichmentService.calculateIndicators(List.of(sapXetr), asOf);

        // The EU listing must have been fetched on its own, not served from the US entry.
        verify(historicalPricePort).getHistoricalPrices(eq(sapXetr), anyInt(), eq(asOf));

        IndicatorSnapshot euSnapshot = euContext.tickerIndicators().get(sapXetr);
        assertNotNull(euSnapshot, "the venue-qualified symbol must be keyed by its own identity");
        IndicatorValue.MovingAverageValue euMa = (IndicatorValue.MovingAverageValue)
            euSnapshot.values().get(IndicatorType.MOVING_AVERAGE);
        assertNotNull(euMa, "EU listing has 250 bars of its own, so indicators must compute");

        assertNotEquals(0, euMa.sma50().compareTo(sma50For(sapUs, asOf)),
            "the EU listing reported the US listing's moving average");
    }

    /** SMA-50 for a symbol, fetched through the service so the cache path is the one under test. */
    private BigDecimal sma50For(Symbol symbol, Instant asOf) {
        IndicatorSnapshot snapshot = enrichmentService.calculateIndicators(List.of(symbol), asOf)
            .tickerIndicators().get(symbol);
        assertNotNull(snapshot);
        IndicatorValue.MovingAverageValue ma = (IndicatorValue.MovingAverageValue)
            snapshot.values().get(IndicatorType.MOVING_AVERAGE);
        assertNotNull(ma);
        return ma.sma50();
    }

    @Test
    @DisplayName("EMA-50 and EMA-200 calculated when sufficient data (200+ days)")
    void emaCalculatedWithSufficientData() {
        // Given: 250 days of price data (enough for EMA-50 and EMA-200)
        List<PriceBar> prices = generatePriceBars(250, new BigDecimal("150.00"), new BigDecimal("0.50"));
        when(historicalPricePort.getHistoricalPrices(eq(AAPL), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), any(Instant.class))).thenReturn(prices);

        // When
        IndicatorContext context = enrichmentService.calculateIndicators(List.of(AAPL));

        // Then
        IndicatorSnapshot snapshot = context.tickerIndicators().get(AAPL);
        IndicatorValue.MovingAverageValue ma =
            (IndicatorValue.MovingAverageValue) snapshot.values().get(IndicatorType.MOVING_AVERAGE);

        assertNotNull(ma.ema50(), "EMA-50 should be calculated with 250 days of data");
        assertNotNull(ma.ema200(), "EMA-200 should be calculated with 250 days of data");
    }

    @Test
    @DisplayName("EMA-50 calculated but EMA-200 null with 150 days of data")
    void ema50CalculatedButEma200NullWithInsufficientData() {
        // Given: 150 days of data (enough for EMA-50, not EMA-200)
        List<PriceBar> prices = generatePriceBars(150, new BigDecimal("100.00"), new BigDecimal("0.30"));
        when(historicalPricePort.getHistoricalPrices(eq(AAPL), eq(IndicatorEnrichmentService.REQUIRED_TRADING_DAYS), any(Instant.class))).thenReturn(prices);

        // When
        IndicatorContext context = enrichmentService.calculateIndicators(List.of(AAPL));

        // Then
        IndicatorSnapshot snapshot = context.tickerIndicators().get(AAPL);
        IndicatorValue.MovingAverageValue ma =
            (IndicatorValue.MovingAverageValue) snapshot.values().get(IndicatorType.MOVING_AVERAGE);

        assertNotNull(ma.ema50(), "EMA-50 should be calculated with 150 days");
        assertNull(ma.ema200(), "EMA-200 should be null with only 150 days");
    }

    @Test
    @DisplayName("Null ticker list treated as empty")
    void nullTickerListTreatedAsEmpty() {
        // When
        IndicatorContext context = enrichmentService.calculateIndicators(null);

        // Then
        assertNotNull(context);
        assertTrue(context.tickerIndicators().isEmpty());
        verifyNoInteractions(historicalPricePort);
    }

    /**
     * Generate synthetic price bars for testing.
     * Creates a trending price series starting from basePrice.
     *
     * @param count Number of price bars to generate
     * @param basePrice Starting price
     * @param dailyChange Average daily price change (can be positive or negative)
     * @return List of price bars ordered by timestamp ascending
     */
    private List<PriceBar> generatePriceBars(int count, BigDecimal basePrice, BigDecimal dailyChange) {
        List<PriceBar> bars = new ArrayList<>();
        BigDecimal currentPrice = basePrice;
        Instant startTime = Instant.now().minusSeconds(count * 86400L);

        for (int i = 0; i < count; i++) {
            // Add some variation: alternate between up and down with overall trend
            BigDecimal variation = (i % 3 == 0)
                ? dailyChange.negate()
                : dailyChange;
            currentPrice = currentPrice.add(variation);

            // Ensure price doesn't go negative
            if (currentPrice.compareTo(BigDecimal.ONE) < 0) {
                currentPrice = BigDecimal.ONE;
            }

            Instant timestamp = startTime.plusSeconds(i * 86400L);

            // Create OHLCV bar
            BigDecimal open = currentPrice.subtract(new BigDecimal("0.10"));
            BigDecimal high = currentPrice.add(new BigDecimal("0.50"));
            BigDecimal low = currentPrice.subtract(new BigDecimal("0.50"));
            BigDecimal close = currentPrice;
            BigDecimal volume = new BigDecimal("1000000");

            bars.add(new PriceBar(timestamp, open, high, low, close, volume));
        }

        return bars;
    }
}
