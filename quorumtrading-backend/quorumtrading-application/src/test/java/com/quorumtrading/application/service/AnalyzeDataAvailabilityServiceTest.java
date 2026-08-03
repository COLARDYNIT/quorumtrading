package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.usecase.command.AnalyzeDataAvailabilityCommand;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.DataAvailability;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.service.USMarketHolidayCalendar;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyzeDataAvailabilityServiceTest {

    @Mock
    private MarketEventRepository marketEventRepository;

    private AnalyzeDataAvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new AnalyzeDataAvailabilityService(marketEventRepository, new USMarketHolidayCalendar());
    }

    @Test
    void shouldAnalyzeDataAvailability() {
        // Given
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();
        TimeRange range = TimeRange.of(from, to);
        Set<Symbol> symbols = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        Set<EventType> types = Set.of(EventType.PRICE);

        List<MarketEvent> events = List.of(
            createMarketEvent("BTC", EventType.PRICE, LocalDate.now())
        );

        when(marketEventRepository.findByTimeRangeAndSymbols(range, symbols))
            .thenReturn(events);

        // When
        var command = new AnalyzeDataAvailabilityCommand(range, symbols, types);
        DataAvailability result = service.execute(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTimeRange()).isEqualTo(range);
        assertThat(result.getSymbols()).isEqualTo(symbols);
        assertThat(result.getDataTypes()).isEqualTo(types);
        verify(marketEventRepository).findByTimeRangeAndSymbols(range, symbols);
    }

    @Test
    void shouldCalculateCoverageMetricsCorrectly() {
        // Given
        LocalDate today = LocalDate.now();
        TimeRange range = TimeRange.of(today, today);
        Set<Symbol> symbols = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        Set<EventType> types = Set.of(EventType.PRICE);

        List<MarketEvent> events = List.of(
            createMarketEvent("BTC", EventType.PRICE, today)
        );

        when(marketEventRepository.findByTimeRangeAndSymbols(range, symbols))
            .thenReturn(events);

        // When
        var command = new AnalyzeDataAvailabilityCommand(range, symbols, types);
        DataAvailability result = service.execute(command);

        // Then
        assertThat(result.getOverallCoverage()).isGreaterThan(java.math.BigDecimal.ZERO);
        assertThat(result.getDailyCoverage()).isNotEmpty();
    }

    @Test
    void shouldHandleEmptyEventsGracefully() {
        // Given
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();
        TimeRange range = TimeRange.of(from, to);
        Set<Symbol> symbols = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        Set<EventType> types = Set.of(EventType.PRICE);

        when(marketEventRepository.findByTimeRangeAndSymbols(range, symbols))
            .thenReturn(List.of());

        // When
        var command = new AnalyzeDataAvailabilityCommand(range, symbols, types);
        DataAvailability result = service.execute(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOverallCoverage()).isEqualByComparingTo(java.math.BigDecimal.ZERO);
    }

    @Test
    void shouldSkipWeekendsForStocks() {
        // Given - a weekend date
        LocalDate saturday = LocalDate.of(2024, 11, 16); // A Saturday
        TimeRange range = TimeRange.of(saturday, saturday);
        Set<Symbol> symbols = Set.of(new Symbol("AAPL", InvestmentType.STOCK));
        Set<EventType> types = Set.of(EventType.PRICE);

        when(marketEventRepository.findByTimeRangeAndSymbols(range, symbols))
            .thenReturn(List.of());

        // When
        var command = new AnalyzeDataAvailabilityCommand(range, symbols, types);
        DataAvailability result = service.execute(command);

        // Then - should not include weekend dates in coverage calculation
        assertThat(result.getDailyCoverage()).isEmpty();
    }

    @Test
    void shouldIncludeWeekendsForCrypto() {
        // Given - a weekend date
        LocalDate saturday = LocalDate.of(2024, 11, 16); // A Saturday
        TimeRange range = TimeRange.of(saturday, saturday);
        Set<Symbol> symbols = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        Set<EventType> types = Set.of(EventType.PRICE);

        List<MarketEvent> events = List.of(
            createMarketEvent("BTC", EventType.PRICE, saturday)
        );

        when(marketEventRepository.findByTimeRangeAndSymbols(range, symbols))
            .thenReturn(events);

        // When
        var command = new AnalyzeDataAvailabilityCommand(range, symbols, types);
        DataAvailability result = service.execute(command);

        // Then - should include weekend dates for crypto
        assertThat(result.getDailyCoverage()).isNotEmpty();
        assertThat(result.getDailyCoverage()).containsKey(saturday);
    }

    @Test
    void shouldHandleMultipleSymbolsAndTypes() {
        // Given
        LocalDate today = LocalDate.now();
        TimeRange range = TimeRange.of(today, today);
        Set<Symbol> symbols = Set.of(
            new Symbol("BTC", InvestmentType.CRYPTO),
            new Symbol("ETH", InvestmentType.CRYPTO)
        );
        Set<EventType> types = Set.of(EventType.PRICE, EventType.NEWS);

        List<MarketEvent> events = List.of(
            createMarketEvent("BTC", EventType.PRICE, today),
            createMarketEvent("BTC", EventType.NEWS, today),
            createMarketEvent("ETH", EventType.PRICE, today)
            // Missing ETH NEWS - but NEWS is market-wide, so this is fine
        );

        when(marketEventRepository.findByTimeRangeAndSymbols(range, symbols))
            .thenReturn(events);

        // When
        var command = new AnalyzeDataAvailabilityCommand(range, symbols, types);
        DataAvailability result = service.execute(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDailyCoverage()).containsKey(today);

        // Overall coverage is 100% because:
        // - PRICE coverage: both BTC and ETH have PRICE events = 100%
        // - NEWS coverage: market-wide, at least one NEWS event exists = 100%
        assertThat(result.getOverallCoverage()).isEqualByComparingTo(new java.math.BigDecimal("100"));

        // Per-type coverage:
        // - PRICE is symbol-specific: 100% (both symbols have price data)
        // - NEWS is market-wide: 100% (any news exists = complete)
        var dailyMetrics = result.getDailyCoverage().get(today);
        assertThat(dailyMetrics.perTypePercentage().get(EventType.PRICE))
            .isEqualByComparingTo(new java.math.BigDecimal("100"));
        assertThat(dailyMetrics.perTypePercentage().get(EventType.NEWS))
            .isEqualByComparingTo(new java.math.BigDecimal("100")); // Market-wide: any news = complete
    }

    @Test
    void shouldShowMissingNewsWhenNoNewsEventsExist() {
        // Given - no NEWS events at all
        LocalDate today = LocalDate.now();
        TimeRange range = TimeRange.of(today, today);
        Set<Symbol> symbols = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        Set<EventType> types = Set.of(EventType.PRICE, EventType.NEWS);

        List<MarketEvent> events = List.of(
            createMarketEvent("BTC", EventType.PRICE, today)
            // No NEWS events at all
        );

        when(marketEventRepository.findByTimeRangeAndSymbols(range, symbols))
            .thenReturn(events);

        // When
        var command = new AnalyzeDataAvailabilityCommand(range, symbols, types);
        DataAvailability result = service.execute(command);

        // Then - NEWS should show 0% coverage (market-wide, no news at all)
        var dailyMetrics = result.getDailyCoverage().get(today);
        assertThat(dailyMetrics.perTypePercentage().get(EventType.NEWS))
            .isEqualByComparingTo(java.math.BigDecimal.ZERO);
        assertThat(dailyMetrics.perTypePercentage().get(EventType.PRICE))
            .isEqualByComparingTo(new java.math.BigDecimal("100"));
    }

    // Holiday integration tests

    @Test
    void shouldSkipUSMarketHolidaysForStocks() {
        // Given - Christmas 2024 is Wednesday Dec 25
        LocalDate christmas = LocalDate.of(2024, 12, 25);
        TimeRange range = TimeRange.of(christmas, christmas);
        Set<Symbol> symbols = Set.of(new Symbol("AAPL", InvestmentType.STOCK));
        Set<EventType> types = Set.of(EventType.PRICE);

        when(marketEventRepository.findByTimeRangeAndSymbols(range, symbols))
            .thenReturn(List.of());

        // When
        var command = new AnalyzeDataAvailabilityCommand(range, symbols, types);
        DataAvailability result = service.execute(command);

        // Then - holiday should be skipped, no coverage entry
        assertThat(result.getDailyCoverage()).isEmpty();
    }

    @Test
    void shouldNotSkipHolidaysForCrypto() {
        // Given - Christmas 2024, but crypto trades 24/7
        LocalDate christmas = LocalDate.of(2024, 12, 25);
        TimeRange range = TimeRange.of(christmas, christmas);
        Set<Symbol> symbols = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        Set<EventType> types = Set.of(EventType.PRICE);

        List<MarketEvent> events = List.of(
            createMarketEvent("BTC", EventType.PRICE, christmas)
        );

        when(marketEventRepository.findByTimeRangeAndSymbols(range, symbols))
            .thenReturn(events);

        // When
        var command = new AnalyzeDataAvailabilityCommand(range, symbols, types);
        DataAvailability result = service.execute(command);

        // Then - crypto should still have coverage on holidays
        assertThat(result.getDailyCoverage()).containsKey(christmas);
    }

    @Test
    void shouldSkipObservedHoliday() {
        // Given - Jul 4 2020 is Saturday → observed Friday Jul 3
        LocalDate observedJuly4 = LocalDate.of(2020, 7, 3);
        TimeRange range = TimeRange.of(observedJuly4, observedJuly4);
        Set<Symbol> symbols = Set.of(new Symbol("AAPL", InvestmentType.STOCK));
        Set<EventType> types = Set.of(EventType.PRICE);

        when(marketEventRepository.findByTimeRangeAndSymbols(range, symbols))
            .thenReturn(List.of());

        // When
        var command = new AnalyzeDataAvailabilityCommand(range, symbols, types);
        DataAvailability result = service.execute(command);

        // Then - observed holiday (Friday) should be skipped
        assertThat(result.getDailyCoverage()).isEmpty();
    }

    @Test
    void shouldSkipGoodFriday() {
        // Given - Good Friday 2024 is Mar 29
        LocalDate goodFriday = LocalDate.of(2024, 3, 29);
        TimeRange range = TimeRange.of(goodFriday, goodFriday);
        Set<Symbol> symbols = Set.of(new Symbol("AAPL", InvestmentType.STOCK));
        Set<EventType> types = Set.of(EventType.PRICE);

        when(marketEventRepository.findByTimeRangeAndSymbols(range, symbols))
            .thenReturn(List.of());

        // When
        var command = new AnalyzeDataAvailabilityCommand(range, symbols, types);
        DataAvailability result = service.execute(command);

        // Then - Good Friday should be skipped
        assertThat(result.getDailyCoverage()).isEmpty();
    }

    private MarketEvent createMarketEvent(String symbol, EventType type, LocalDate date) {
        Instant timestamp = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        return new MarketEvent(
            timestamp,
            type,
            "test-source",
            symbol,
            Map.of("test", "data")
        );
    }
}
