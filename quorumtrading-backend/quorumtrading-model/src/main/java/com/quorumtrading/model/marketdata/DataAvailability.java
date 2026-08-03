package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregate root representing the availability of market data for a specific time range,
 * symbols, and data types.
 *
 * <p>This aggregate is responsible for:</p>
 * <ul>
 *   <li>Determining if data coverage is sufficient for analysis/backtesting</li>
 *   <li>Calculating overall coverage metrics across time periods</li>
 *   <li>Identifying gaps in data coverage that may require backfilling</li>
 * </ul>
 *
 * <p><b>DDD Design:</b></p>
 * <ul>
 *   <li>Aggregate Root - encapsulates data availability business logic</li>
 *   <li>Uses value objects (Symbol, TimeRange, DataCoverageMetrics, MarketDataGap)</li>
 *   <li>Enforces invariants around coverage calculation and gap identification</li>
 * </ul>
 */
public class DataAvailability {
    private static final BigDecimal SUFFICIENCY_THRESHOLD = new BigDecimal("80");
    private static final BigDecimal FULL_COVERAGE = new BigDecimal("100");

    private final TimeRange timeRange;
    private final Set<Symbol> symbols;
    private final Set<EventType> dataTypes;
    private final Map<LocalDate, DataCoverageMetrics> dailyCoverage;

    private DataAvailability(TimeRange timeRange, Set<Symbol> symbols,
                            Set<EventType> dataTypes,
                            Map<LocalDate, DataCoverageMetrics> dailyCoverage) {
        if (timeRange == null) {
            throw new IllegalArgumentException("TimeRange cannot be null");
        }
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("Symbols cannot be null or empty");
        }
        if (dataTypes == null || dataTypes.isEmpty()) {
            throw new IllegalArgumentException("DataTypes cannot be null or empty");
        }
        if (dailyCoverage == null) {
            throw new IllegalArgumentException("DailyCoverage cannot be null");
        }

        this.timeRange = timeRange;
        this.symbols = Set.copyOf(symbols);
        this.dataTypes = Set.copyOf(dataTypes);
        this.dailyCoverage = Map.copyOf(dailyCoverage);
    }

    /**
     * Factory method to create a DataAvailability aggregate.
     *
     * @param timeRange the time range for which data availability is being analyzed
     * @param symbols the set of symbols to analyze
     * @param dataTypes the set of data types to analyze
     * @param dailyCoverage the coverage metrics for each date in the range
     * @return a new DataAvailability instance
     */
    public static DataAvailability of(TimeRange timeRange, Set<Symbol> symbols,
                                     Set<EventType> dataTypes,
                                     Map<LocalDate, DataCoverageMetrics> dailyCoverage) {
        return new DataAvailability(timeRange, symbols, dataTypes, dailyCoverage);
    }

    /**
     * Check if the overall data coverage is sufficient for analysis.
     * Uses the default threshold of 80%.
     *
     * @return true if overall coverage meets or exceeds the threshold
     */
    public boolean isSufficient() {
        return getOverallCoverage().compareTo(SUFFICIENCY_THRESHOLD) >= 0;
    }

    /**
     * Calculate the overall coverage percentage across all dates in the time range.
     * This is the average of all daily coverage percentages.
     *
     * @return the overall coverage percentage (0-100)
     */
    public BigDecimal getOverallCoverage() {
        if (dailyCoverage.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = dailyCoverage.values().stream()
            .map(DataCoverageMetrics::overallPercentage)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(new BigDecimal(dailyCoverage.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Identify gaps in data coverage where data is missing or incomplete.
     *
     * <p>Gap creation rules:</p>
     * <ul>
     *   <li><b>PRICE:</b> Symbol-specific gaps (one per symbol per incomplete day)</li>
     *   <li><b>NEWS/SOCIAL:</b> Market-wide gaps (one per incomplete day, symbol=null)</li>
     * </ul>
     *
     * <p>This design reflects the reality that:</p>
     * <ul>
     *   <li>Price data is inherently symbol-specific (each stock has its own price)</li>
     *   <li>News/social data is market-wide (general market news, not ticker-specific)</li>
     *   <li>Absence of ticker-specific news is not a gap - there may simply be no news</li>
     * </ul>
     *
     * <p>Consecutive gaps for the same symbol/type are merged into a single gap
     * spanning multiple dates to reduce API calls and improve UX.</p>
     *
     * @return list of identified gaps, merged for consecutive dates
     */
    public List<MarketDataGap> identifyGaps() {
        List<MarketDataGap> gaps = new ArrayList<>();

        // Sort dates for consistent gap merging
        List<LocalDate> sortedDates = dailyCoverage.keySet().stream()
            .sorted()
            .toList();

        for (LocalDate date : sortedDates) {
            DataCoverageMetrics metrics = dailyCoverage.get(date);
            if (metrics == null || metrics.isComplete()) {
                continue;
            }

            for (EventType type : dataTypes) {
                BigDecimal typeCoverage = metrics
                    .perTypePercentage()
                    .getOrDefault(type, BigDecimal.ZERO);

                // Only create gaps for data types that are actually missing
                if (typeCoverage.compareTo(FULL_COVERAGE) < 0) {
                    if (isMarketWideDataType(type)) {
                        // NEWS/SOCIAL: One market-wide gap per day (symbol=null)
                        gaps.add(new MarketDataGap(null, type, TimeRange.of(date, date)));
                    } else {
                        // PRICE: Symbol-specific gaps
                        for (Symbol symbol : symbols) {
                            // Check if this specific symbol is missing data
                            BigDecimal symbolCoverage = metrics
                                .perSymbolPercentage()
                                .getOrDefault(symbol, BigDecimal.ZERO);
                            if (symbolCoverage.compareTo(FULL_COVERAGE) < 0) {
                                gaps.add(new MarketDataGap(symbol, type, TimeRange.of(date, date)));
                            }
                        }
                    }
                }
            }
        }

        return mergeConsecutiveGaps(gaps);
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
     * Merge consecutive date gaps for the same symbol/type combination.
     *
     * <p>Combines gaps like:</p>
     * <ul>
     *   <li>BTC/PRICE 2024-01-01 to 2024-01-01</li>
     *   <li>BTC/PRICE 2024-01-02 to 2024-01-02</li>
     *   <li>BTC/PRICE 2024-01-03 to 2024-01-03</li>
     * </ul>
     * into:
     * <ul>
     *   <li>BTC/PRICE 2024-01-01 to 2024-01-03</li>
     * </ul>
     *
     * @param gaps the list of gaps to merge
     * @return merged list with consecutive dates combined
     */
    private List<MarketDataGap> mergeConsecutiveGaps(List<MarketDataGap> gaps) {
        if (gaps.isEmpty()) {
            return gaps;
        }

        // Group gaps by symbol+type (symbol can be null for market-wide)
        Map<String, List<MarketDataGap>> groupedGaps = gaps.stream()
            .collect(Collectors.groupingBy(gap -> {
                String symbolKey = gap.symbol() != null ? gap.symbol().ticker() : "MARKET";
                return symbolKey + ":" + gap.dataType();
            }));

        List<MarketDataGap> mergedGaps = new ArrayList<>();

        for (List<MarketDataGap> group : groupedGaps.values()) {
            // Sort by start date
            List<MarketDataGap> sorted = group.stream()
                .sorted((a, b) -> a.period().getStart().compareTo(b.period().getStart()))
                .toList();

            MarketDataGap current = sorted.get(0);

            for (int i = 1; i < sorted.size(); i++) {
                MarketDataGap next = sorted.get(i);

                // Check if dates are consecutive (next day)
                if (current.period().getEnd().plusDays(1).equals(next.period().getStart())) {
                    // Merge: extend current gap to include next
                    current = new MarketDataGap(
                        current.symbol(),
                        current.dataType(),
                        TimeRange.of(current.period().getStart(), next.period().getEnd())
                    );
                } else {
                    // Not consecutive: save current and start new
                    mergedGaps.add(current);
                    current = next;
                }
            }

            // Don't forget the last gap
            mergedGaps.add(current);
        }

        // Sort final result by date for consistent ordering
        return mergedGaps.stream()
            .sorted((a, b) -> {
                int dateCompare = a.period().getStart().compareTo(b.period().getStart());
                if (dateCompare != 0) return dateCompare;
                // Secondary sort by type, then symbol
                int typeCompare = a.dataType().compareTo(b.dataType());
                if (typeCompare != 0) return typeCompare;
                String aSymbol = a.symbol() != null ? a.symbol().ticker() : "";
                String bSymbol = b.symbol() != null ? b.symbol().ticker() : "";
                return aSymbol.compareTo(bSymbol);
            })
            .toList();
    }

    // Getters for aggregate properties

    public TimeRange getTimeRange() {
        return timeRange;
    }

    public Set<Symbol> getSymbols() {
        return symbols;
    }

    public Set<EventType> getDataTypes() {
        return dataTypes;
    }

    public Map<LocalDate, DataCoverageMetrics> getDailyCoverage() {
        return dailyCoverage;
    }
}
