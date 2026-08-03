package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.application.usecase.AnalyzeDataAvailabilityUseCase;
import com.quorumtrading.application.usecase.command.AnalyzeDataAvailabilityCommand;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.DataAvailability;
import com.quorumtrading.model.marketdata.DataCoverageMetrics;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.service.USMarketHolidayCalendar;
import com.quorumtrading.model.valueobject.Symbol;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service implementation for analyzing market data availability.
 *
 * <p>This service:</p>
 * <ul>
 *   <li>Queries market events from the repository</li>
 *   <li>Calculates daily coverage metrics for symbols and data types</li>
 *   <li>Handles market calendar logic (weekends for stocks, 24/7 for crypto)</li>
 *   <li>Returns a DataAvailability aggregate with complete coverage analysis</li>
 * </ul>
 */
public class AnalyzeDataAvailabilityService implements AnalyzeDataAvailabilityUseCase {

    private final MarketEventRepository marketEventRepository;
    private final USMarketHolidayCalendar holidayCalendar;

    public AnalyzeDataAvailabilityService(MarketEventRepository marketEventRepository,
                                          USMarketHolidayCalendar holidayCalendar) {
        this.marketEventRepository = marketEventRepository;
        this.holidayCalendar = holidayCalendar;
    }

    @Override
    public DataAvailability execute(AnalyzeDataAvailabilityCommand command) {
        // Fetch all events in range
        List<MarketEvent> events = marketEventRepository.findByTimeRangeAndSymbols(
            command.timeRange(),
            command.symbols()
        );

        // Group events by date
        Map<LocalDate, List<MarketEvent>> eventsByDate = events.stream()
            .collect(Collectors.groupingBy(e -> e.timestamp().atZone(java.time.ZoneOffset.UTC).toLocalDate()));

        // Calculate coverage for each day
        Map<LocalDate, DataCoverageMetrics> dailyCoverage = new HashMap<>();

        LocalDate current = command.timeRange().getStart();
        while (!current.isAfter(command.timeRange().getEnd())) {
            if (!isMarketClosed(current, command.symbols())) {
                DataCoverageMetrics metrics = calculateDailyCoverage(
                    current,
                    eventsByDate.getOrDefault(current, List.of()),
                    command.symbols(),
                    command.dataTypes()
                );
                dailyCoverage.put(current, metrics);
            }
            current = current.plusDays(1);
        }

        return DataAvailability.of(
            command.timeRange(),
            command.symbols(),
            command.dataTypes(),
            dailyCoverage
        );
    }

    /**
     * Calculate coverage metrics for a single day.
     *
     * <p>Coverage is calculated differently based on data type:</p>
     * <ul>
     *   <li><b>PRICE:</b> Symbol-specific - 100% if each symbol has price events</li>
     *   <li><b>NEWS/SOCIAL:</b> Market-wide - 100% if ANY news/social events exist for the day</li>
     * </ul>
     *
     * <p>This reflects the reality that:</p>
     * <ul>
     *   <li>Price data is inherently per-symbol (each stock has its own price)</li>
     *   <li>News is market-wide (general market news, not tied to specific symbols)</li>
     *   <li>Absence of ticker-specific news is not a coverage gap</li>
     * </ul>
     *
     * @param date the date to calculate coverage for
     * @param events the events available for this date
     * @param expectedSymbols the symbols we expect data for
     * @param expectedTypes the data types we expect
     * @return coverage metrics for this date
     */
    private DataCoverageMetrics calculateDailyCoverage(LocalDate date,
                                                      List<MarketEvent> events,
                                                      Set<Symbol> expectedSymbols,
                                                      Set<EventType> expectedTypes) {
        // Calculate per-symbol coverage (for PRICE data)
        Map<Symbol, BigDecimal> perSymbolCoverage = new HashMap<>();
        for (Symbol symbol : expectedSymbols) {
            // Only count PRICE events for per-symbol coverage
            long symbolPriceEvents = events.stream()
                .filter(e -> e.symbol().equals(symbol.ticker()) && e.eventType() == EventType.PRICE)
                .count();
            BigDecimal coverage = symbolPriceEvents > 0 ? new BigDecimal("100") : BigDecimal.ZERO;
            perSymbolCoverage.put(symbol, coverage);
        }

        // Calculate per-type coverage
        Map<EventType, BigDecimal> perTypeCoverage = new HashMap<>();
        for (EventType type : expectedTypes) {
            BigDecimal coverage;

            if (isMarketWideDataType(type)) {
                // NEWS/SOCIAL: 100% if ANY events of this type exist for the day
                // Check for both "MARKET" symbol and any other symbol
                boolean hasMarketWideEvents = events.stream()
                    .anyMatch(e -> e.eventType() == type);
                coverage = hasMarketWideEvents ? new BigDecimal("100") : BigDecimal.ZERO;
            } else {
                // PRICE: Symbol-specific - count how many symbols have data
                long symbolsWithType = expectedSymbols.stream()
                    .filter(symbol -> events.stream()
                        .anyMatch(e -> e.symbol().equals(symbol.ticker()) && e.eventType() == type))
                    .count();

                long expectedCount = expectedSymbols.size();
                coverage = expectedCount > 0
                    ? new BigDecimal(symbolsWithType * 100)
                        .divide(new BigDecimal(expectedCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            }

            perTypeCoverage.put(type, coverage.min(new BigDecimal("100")));
        }

        // Calculate overall coverage
        // Weight PRICE coverage per symbol, NEWS/SOCIAL as binary per day
        BigDecimal overall = calculateOverallCoverage(perSymbolCoverage, perTypeCoverage, expectedTypes);

        return new DataCoverageMetrics(overall, perTypeCoverage, perSymbolCoverage);
    }

    /**
     * Check if a data type is market-wide (not symbol-specific).
     *
     * @param type the event type to check
     * @return true if this type represents market-wide data
     */
    private boolean isMarketWideDataType(EventType type) {
        return type == EventType.NEWS || type == EventType.SOCIAL;
    }

    /**
     * Calculate overall coverage combining symbol-specific and market-wide data.
     *
     * <p>Strategy:</p>
     * <ul>
     *   <li>If only PRICE requested: average of per-symbol coverage</li>
     *   <li>If only NEWS/SOCIAL requested: binary (100% or 0%)</li>
     *   <li>If mixed: weighted average giving equal weight to each type category</li>
     * </ul>
     */
    private BigDecimal calculateOverallCoverage(Map<Symbol, BigDecimal> perSymbolCoverage,
                                                Map<EventType, BigDecimal> perTypeCoverage,
                                                Set<EventType> expectedTypes) {
        if (expectedTypes.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Separate market-wide and symbol-specific types
        boolean hasSymbolSpecific = expectedTypes.stream().anyMatch(t -> !isMarketWideDataType(t));
        boolean hasMarketWide = expectedTypes.stream().anyMatch(this::isMarketWideDataType);

        if (hasSymbolSpecific && hasMarketWide) {
            // Mixed: average of symbol-specific coverage and market-wide coverage
            BigDecimal symbolCoverage = perSymbolCoverage.isEmpty()
                ? BigDecimal.ZERO
                : perSymbolCoverage.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(perSymbolCoverage.size()), 2, RoundingMode.HALF_UP);

            BigDecimal marketWideCoverage = perTypeCoverage.entrySet().stream()
                .filter(e -> isMarketWideDataType(e.getKey()))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(expectedTypes.stream().filter(this::isMarketWideDataType).count()), 2, RoundingMode.HALF_UP);

            return symbolCoverage.add(marketWideCoverage)
                .divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
        } else if (hasSymbolSpecific) {
            // Only symbol-specific: average of per-symbol coverage
            return perSymbolCoverage.isEmpty()
                ? BigDecimal.ZERO
                : perSymbolCoverage.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(perSymbolCoverage.size()), 2, RoundingMode.HALF_UP);
        } else {
            // Only market-wide: average of market-wide type coverages
            long marketWideCount = expectedTypes.stream().filter(this::isMarketWideDataType).count();
            return marketWideCount > 0
                ? perTypeCoverage.entrySet().stream()
                    .filter(e -> isMarketWideDataType(e.getKey()))
                    .map(Map.Entry::getValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(marketWideCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        }
    }

    /**
     * Determine if the market is closed for the given date and symbols.
     *
     * <p>Market calendar rules:</p>
     * <ul>
     *   <li>Crypto: Trades 24/7, never closed</li>
     *   <li>Stocks: Closed on weekends (Saturday/Sunday) and US market holidays</li>
     *   <li>Mixed: If any symbol is crypto, treat as open</li>
     * </ul>
     *
     * @param date the date to check
     * @param symbols the symbols being analyzed
     * @return true if the market is closed for all symbols on this date
     */
    private boolean isMarketClosed(LocalDate date, Set<Symbol> symbols) {
        // If any symbol is crypto, market is open
        boolean hasCrypto = symbols.stream()
            .anyMatch(s -> s.type() == InvestmentType.CRYPTO);

        if (hasCrypto) {
            return false; // Crypto markets trade 24/7
        }

        // For stocks, check if it's a weekend or US market holiday
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return true;
        }

        return holidayCalendar.isHoliday(date);
    }
}
