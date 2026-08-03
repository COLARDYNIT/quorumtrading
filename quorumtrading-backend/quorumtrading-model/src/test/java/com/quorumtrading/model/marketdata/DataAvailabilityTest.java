package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.TimeRange;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class DataAvailabilityTest {

    @Test
    void shouldCalculateSufficiency() {
        TimeRange range = TimeRange.of(LocalDate.now().minusDays(7), LocalDate.now());
        Set<Symbol> symbols = Set.of(new Symbol("BTC", InvestmentType.CRYPTO), new Symbol("ETH", InvestmentType.CRYPTO));
        Set<EventType> types = Set.of(EventType.PRICE, EventType.NEWS);

        Map<LocalDate, DataCoverageMetrics> dailyCoverage = new HashMap<>();
        dailyCoverage.put(LocalDate.now(), new DataCoverageMetrics(
            new BigDecimal("95"),
            Map.of(),
            Map.of()
        ));

        DataAvailability availability = DataAvailability.of(
            range, symbols, types, dailyCoverage
        );

        assertThat(availability.isSufficient()).isTrue();
        assertThat(availability.getOverallCoverage()).isEqualByComparingTo(new BigDecimal("95"));
    }

    @Test
    void shouldIdentifyGaps() {
        TimeRange range = TimeRange.of(LocalDate.now().minusDays(2), LocalDate.now());
        Set<Symbol> symbols = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        Set<EventType> types = Set.of(EventType.PRICE);

        Map<LocalDate, DataCoverageMetrics> dailyCoverage = new HashMap<>();
        dailyCoverage.put(LocalDate.now(), new DataCoverageMetrics(
            new BigDecimal("0"),
            Map.of(),
            Map.of()
        ));

        DataAvailability availability = DataAvailability.of(
            range, symbols, types, dailyCoverage
        );

        List<MarketDataGap> gaps = availability.identifyGaps();
        assertThat(gaps).isNotEmpty();
    }

    @Test
    void shouldOnlyCreateGapsForMissingDataTypes() {
        // Setup: 2 symbols, 3 data types
        LocalDate testDate = LocalDate.now();
        TimeRange range = TimeRange.of(testDate, testDate);
        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        Symbol eth = new Symbol("ETH", InvestmentType.CRYPTO);
        Set<Symbol> symbols = Set.of(btc, eth);
        Set<EventType> types = Set.of(
            EventType.PRICE,
            EventType.NEWS,
            EventType.SOCIAL
        );

        // Scenario: PRICE has 100% coverage, NEWS has 50% coverage, SOCIAL has 0% coverage
        Map<LocalDate, DataCoverageMetrics> dailyCoverage = new HashMap<>();
        dailyCoverage.put(testDate, new DataCoverageMetrics(
            new BigDecimal("50"),  // Overall 50%
            Map.of(
                EventType.PRICE, new BigDecimal("100"),           // Complete
                EventType.NEWS, new BigDecimal("50"),             // Incomplete
                EventType.SOCIAL, new BigDecimal("0")             // Missing
            ),
            Map.of(
                btc, new BigDecimal("50"),
                eth, new BigDecimal("50")
            )
        ));

        DataAvailability availability = DataAvailability.of(
            range, symbols, types, dailyCoverage
        );

        // Act: Identify gaps
        List<MarketDataGap> gaps = availability.identifyGaps();

        // Assert: Should only have gaps for NEWS and SOCIAL, NOT for PRICE
        // NEWS and SOCIAL are market-wide (1 gap each), not symbol-specific
        // Expected: 1 NEWS gap + 1 SOCIAL gap = 2 gaps (both market-wide)
        assertThat(gaps).hasSize(2);

        // Verify no gaps for PRICE (it's at 100%)
        long priceGaps = gaps.stream()
            .filter(gap -> gap.dataType() == EventType.PRICE)
            .count();
        assertThat(priceGaps).isZero();

        // Verify gaps exist for NEWS and SOCIAL (one each, market-wide)
        long newsGaps = gaps.stream()
            .filter(gap -> gap.dataType() == EventType.NEWS)
            .count();
        assertThat(newsGaps).isEqualTo(1);  // Market-wide, not per-symbol

        long socialGaps = gaps.stream()
            .filter(gap -> gap.dataType() == EventType.SOCIAL)
            .count();
        assertThat(socialGaps).isEqualTo(1);  // Market-wide, not per-symbol

        // Verify NEWS and SOCIAL gaps are market-wide (null symbol)
        assertThat(gaps).allMatch(MarketDataGap::isMarketWide);
    }

    @Test
    void shouldNotCreateGapsWhenAllDataTypesAreComplete() {
        // Setup: All data types at 100% coverage
        LocalDate testDate = LocalDate.now();
        TimeRange range = TimeRange.of(testDate, testDate);
        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        Set<Symbol> symbols = Set.of(btc);
        Set<EventType> types = Set.of(
            EventType.PRICE,
            EventType.NEWS
        );

        Map<LocalDate, DataCoverageMetrics> dailyCoverage = new HashMap<>();
        dailyCoverage.put(testDate, new DataCoverageMetrics(
            new BigDecimal("100"),  // Overall 100%
            Map.of(
                EventType.PRICE, new BigDecimal("100"),
                EventType.NEWS, new BigDecimal("100")
            ),
            Map.of(btc, new BigDecimal("100"))
        ));

        DataAvailability availability = DataAvailability.of(
            range, symbols, types, dailyCoverage
        );

        // Act: Identify gaps
        List<MarketDataGap> gaps = availability.identifyGaps();

        // Assert: No gaps should exist
        assertThat(gaps).isEmpty();
    }

    @Test
    void shouldCreateSymbolSpecificGapsForPriceData() {
        // Setup: 2 symbols, PRICE data type with incomplete coverage
        LocalDate testDate = LocalDate.now();
        TimeRange range = TimeRange.of(testDate, testDate);
        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        Symbol eth = new Symbol("ETH", InvestmentType.CRYPTO);
        Set<Symbol> symbols = Set.of(btc, eth);
        Set<EventType> types = Set.of(EventType.PRICE);

        // Scenario: BTC has 100% coverage, ETH has 0% coverage
        Map<LocalDate, DataCoverageMetrics> dailyCoverage = new HashMap<>();
        dailyCoverage.put(testDate, new DataCoverageMetrics(
            new BigDecimal("50"),  // Overall 50%
            Map.of(EventType.PRICE, new BigDecimal("50")),
            Map.of(
                btc, new BigDecimal("100"),  // BTC is complete
                eth, new BigDecimal("0")     // ETH is missing
            )
        ));

        DataAvailability availability = DataAvailability.of(
            range, symbols, types, dailyCoverage
        );

        // Act: Identify gaps
        List<MarketDataGap> gaps = availability.identifyGaps();

        // Assert: Should have 1 gap for ETH PRICE only (BTC is complete)
        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0).symbol()).isEqualTo(eth);
        assertThat(gaps.get(0).dataType()).isEqualTo(EventType.PRICE);
        assertThat(gaps.get(0).isMarketWide()).isFalse();
    }

    @Test
    void shouldMergeConsecutiveGaps() {
        // Setup: 3 consecutive days with missing PRICE data
        LocalDate day1 = LocalDate.of(2024, 1, 1);
        LocalDate day2 = LocalDate.of(2024, 1, 2);
        LocalDate day3 = LocalDate.of(2024, 1, 3);
        TimeRange range = TimeRange.of(day1, day3);
        Symbol btc = new Symbol("BTC", InvestmentType.CRYPTO);
        Set<Symbol> symbols = Set.of(btc);
        Set<EventType> types = Set.of(EventType.PRICE);

        // All 3 days have 0% coverage for BTC
        Map<LocalDate, DataCoverageMetrics> dailyCoverage = new HashMap<>();
        for (LocalDate date : List.of(day1, day2, day3)) {
            dailyCoverage.put(date, new DataCoverageMetrics(
                BigDecimal.ZERO,
                Map.of(EventType.PRICE, BigDecimal.ZERO),
                Map.of(btc, BigDecimal.ZERO)
            ));
        }

        DataAvailability availability = DataAvailability.of(
            range, symbols, types, dailyCoverage
        );

        // Act: Identify gaps
        List<MarketDataGap> gaps = availability.identifyGaps();

        // Assert: Should have 1 merged gap spanning all 3 days
        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0).symbol()).isEqualTo(btc);
        assertThat(gaps.get(0).dataType()).isEqualTo(EventType.PRICE);
        assertThat(gaps.get(0).period().getStart()).isEqualTo(day1);
        assertThat(gaps.get(0).period().getEnd()).isEqualTo(day3);
    }

    @Test
    void shouldValidateConstructorParameters() {
        TimeRange range = TimeRange.of(LocalDate.now(), LocalDate.now());
        Set<Symbol> symbols = Set.of(new Symbol("BTC", InvestmentType.CRYPTO));
        Set<EventType> types = Set.of(EventType.PRICE);
        Map<LocalDate, DataCoverageMetrics> coverage = Map.of();

        // Test null timeRange
        assertThatThrownBy(() -> DataAvailability.of(null, symbols, types, coverage))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("TimeRange cannot be null");

        // Test null symbols
        assertThatThrownBy(() -> DataAvailability.of(range, null, types, coverage))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Symbols cannot be null or empty");

        // Test empty symbols
        assertThatThrownBy(() -> DataAvailability.of(range, Set.of(), types, coverage))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Symbols cannot be null or empty");

        // Test null dataTypes
        assertThatThrownBy(() -> DataAvailability.of(range, symbols, null, coverage))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DataTypes cannot be null or empty");

        // Test empty dataTypes
        assertThatThrownBy(() -> DataAvailability.of(range, symbols, Set.of(), coverage))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DataTypes cannot be null or empty");

        // Test null dailyCoverage
        assertThatThrownBy(() -> DataAvailability.of(range, symbols, types, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DailyCoverage cannot be null");
    }
}
