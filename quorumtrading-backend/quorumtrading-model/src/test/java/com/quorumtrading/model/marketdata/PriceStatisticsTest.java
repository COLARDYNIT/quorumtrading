package com.quorumtrading.model.marketdata;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PriceStatisticsTest {

    @Test
    void shouldCreateEmptyStatisticsWhenNoPrices() {
        PriceStatistics stats = PriceStatistics.fromPrices(List.of());

        assertFalse(stats.hasData());
        assertEquals(0, stats.dataPointCount());
        assertNull(stats.latestPrice());
        assertEquals(PriceStatistics.Trend.FLAT, stats.trend());
    }

    @Test
    void shouldCreateEmptyStatisticsWhenNullPrices() {
        PriceStatistics stats = PriceStatistics.fromPrices(null);

        assertFalse(stats.hasData());
        assertEquals(0, stats.dataPointCount());
    }

    @Test
    void shouldCalculateBasicStatistics() {
        List<BigDecimal> prices = List.of(
            new BigDecimal("100.00"),
            new BigDecimal("105.00"),
            new BigDecimal("103.00"),
            new BigDecimal("110.00")
        );

        PriceStatistics stats = PriceStatistics.fromPrices(prices);

        assertTrue(stats.hasData());
        assertEquals(4, stats.dataPointCount());
        assertEquals(new BigDecimal("100.00"), stats.earliestPrice());
        assertEquals(new BigDecimal("110.00"), stats.latestPrice());
        assertEquals(new BigDecimal("100.00"), stats.minPrice());
        assertEquals(new BigDecimal("110.00"), stats.maxPrice());
    }

    @Test
    void shouldCalculatePriceChangePercent() {
        List<BigDecimal> prices = List.of(
            new BigDecimal("100.00"),
            new BigDecimal("110.00")
        );

        PriceStatistics stats = PriceStatistics.fromPrices(prices);

        // 10% increase
        assertEquals(new BigDecimal("10.00"), stats.priceChangePercent());
        assertEquals(PriceStatistics.Trend.UP, stats.trend());
    }

    @Test
    void shouldDetectDownTrend() {
        List<BigDecimal> prices = List.of(
            new BigDecimal("110.00"),
            new BigDecimal("100.00")
        );

        PriceStatistics stats = PriceStatistics.fromPrices(prices);

        // -9.09% decrease
        assertTrue(stats.priceChangePercent().compareTo(BigDecimal.ZERO) < 0);
        assertEquals(PriceStatistics.Trend.DOWN, stats.trend());
    }

    @Test
    void shouldDetectFlatTrend() {
        List<BigDecimal> prices = List.of(
            new BigDecimal("100.00"),
            new BigDecimal("100.00")
        );

        PriceStatistics stats = PriceStatistics.fromPrices(prices);

        assertEquals(new BigDecimal("0.00"), stats.priceChangePercent());
        assertEquals(PriceStatistics.Trend.FLAT, stats.trend());
    }

    @Test
    void shouldCalculateVolatility() {
        List<BigDecimal> prices = List.of(
            new BigDecimal("100.00"),
            new BigDecimal("105.00"),
            new BigDecimal("95.00"),
            new BigDecimal("110.00"),
            new BigDecimal("90.00")
        );

        PriceStatistics stats = PriceStatistics.fromPrices(prices);

        assertNotNull(stats.volatility());
        assertTrue(stats.volatility().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void shouldClassifyVolatilityLevels() {
        assertEquals(PriceStatistics.VolatilityLevel.LOW,
            PriceStatistics.VolatilityLevel.from(new BigDecimal("1.0")));

        assertEquals(PriceStatistics.VolatilityLevel.MEDIUM,
            PriceStatistics.VolatilityLevel.from(new BigDecimal("3.0")));

        assertEquals(PriceStatistics.VolatilityLevel.HIGH,
            PriceStatistics.VolatilityLevel.from(new BigDecimal("6.0")));
    }

    @Test
    void shouldHandleNullPricesInList() {
        // Use Arrays.asList instead of List.of to allow null values
        List<BigDecimal> prices = java.util.Arrays.asList(
            new BigDecimal("100.00"),
            null,
            new BigDecimal("110.00")
        );

        PriceStatistics stats = PriceStatistics.fromPrices(prices);

        assertEquals(2, stats.dataPointCount());
        assertEquals(new BigDecimal("100.00"), stats.earliestPrice());
        assertEquals(new BigDecimal("110.00"), stats.latestPrice());
    }

    @Test
    void shouldFilterOutZeroAndNegativePrices() {
        List<BigDecimal> prices = List.of(
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            new BigDecimal("-10.00"),
            new BigDecimal("110.00")
        );

        PriceStatistics stats = PriceStatistics.fromPrices(prices);

        assertEquals(2, stats.dataPointCount());
        assertEquals(new BigDecimal("100.00"), stats.earliestPrice());
    }

    @Test
    void shouldReturnNullVolatilityWithSinglePrice() {
        List<BigDecimal> prices = List.of(new BigDecimal("100.00"));

        PriceStatistics stats = PriceStatistics.fromPrices(prices);

        assertNull(stats.volatility());
        assertEquals(PriceStatistics.VolatilityLevel.LOW, stats.getVolatilityLevel());
    }

    @Test
    void shouldFormatToStringCorrectly() {
        List<BigDecimal> prices = List.of(
            new BigDecimal("100.00"),
            new BigDecimal("110.00")
        );

        PriceStatistics stats = PriceStatistics.fromPrices(prices);
        String str = stats.toString();

        assertTrue(str.contains("UP"));
        assertTrue(str.contains("100.00"));
        assertTrue(str.contains("110.00"));
    }

    @Test
    void shouldFormatEmptyStatisticsToString() {
        PriceStatistics stats = PriceStatistics.emptyStatistics();
        assertEquals("PriceStatistics[no data]", stats.toString());
    }

    @Test
    void shouldThrowExceptionForNegativeDataPointCount() {
        assertThrows(IllegalArgumentException.class, () -> {
            new PriceStatistics(null, null, null, null, null, null,
                PriceStatistics.Trend.FLAT, -1);
        });
    }

    @Test
    void shouldDetermineTrendFromChangePercent() {
        assertEquals(PriceStatistics.Trend.UP,
            PriceStatistics.Trend.from(new BigDecimal("5.0")));

        assertEquals(PriceStatistics.Trend.DOWN,
            PriceStatistics.Trend.from(new BigDecimal("-5.0")));

        assertEquals(PriceStatistics.Trend.FLAT,
            PriceStatistics.Trend.from(BigDecimal.ZERO));

        assertEquals(PriceStatistics.Trend.FLAT,
            PriceStatistics.Trend.from(null));
    }
}
