package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.Symbol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Technical agent renders {@code PriceStatistics.latestPrice()} as "Current: $X" while the
 * Orchestrator renders {@link MarketDataContext#getLatestPrice} as "At current price $Y". Both are
 * read from the same context in the same consensus round, so X and Y must describe the same bar —
 * otherwise the stored transcript shows two agents contradicting each other on the current price,
 * and the agent that decides timing evaluates its rules against a price the order will not fill at.
 *
 * <p>The per-timeframe buckets are filled by independent, separately-limited repository queries, so
 * a wider bucket is not reliably a superset of a narrower one. These tests pin the invariant under
 * exactly that condition.</p>
 */
class MarketDataContextLatestPriceConsistencyTest {

    /** A Monday stock evaluation at 15:00Z, as a backtest schedule produces. */
    private static final Instant ANALYSIS_TIME = Instant.parse("2026-02-02T15:00:00Z");

    /** MarketDataContext.from() builds symbols as STOCK regardless of asset, so lookups must match. */
    private static final Symbol NVDA = new Symbol("NVDA", InvestmentType.STOCK);

    // The real bars behind the observed failure.
    private static final MarketEvent JAN_29 = price("NVDA", "2026-01-29T05:00:00Z", "192.49");
    private static final MarketEvent JAN_30 = price("NVDA", "2026-01-30T05:00:00Z", "191.13");
    private static final MarketEvent FEB_02 = price("NVDA", "2026-02-02T05:00:00Z", "185.66");

    private static MarketEvent price(String symbol, String timestamp, String close) {
        return new MarketEvent(
            Instant.parse(timestamp), EventType.PRICE, "alpaca", symbol, Map.of("price", close));
    }

    private static MarketEvent news(String symbol, String timestamp, String headline) {
        return new MarketEvent(
            Instant.parse(timestamp), EventType.NEWS, "finnhub", symbol, Map.of("headline", headline));
    }

    private static MarketDataContext contextOf(Map<Timeframe, List<MarketEvent>> byTimeframe) {
        return MarketDataContext.from(new HashMap<>(byTimeframe), Set.of("NVDA"), ANALYSIS_TIME);
    }

    @Nested
    @DisplayName("Technical and Orchestrator agree on the current price")
    class LatestPriceAgreement {

        @Test
        @DisplayName("when the 24h window holds a single bar and wider buckets are not supersets")
        void agreesWhenNarrowWindowHasOneBarAndBucketsDisagree() {
            // Exactly the production shape: the 24h window catches only that morning's bar, while
            // the 7d bucket — filled by its own limited query — happens to stop short of it.
            MarketDataContext context = contextOf(Map.of(
                Timeframe.LAST_24H, List.of(FEB_02),
                Timeframe.LAST_7D, List.of(JAN_29, JAN_30)
            ));

            BigDecimal orchestratorPrice = context.getLatestPrice(NVDA).orElseThrow();
            BigDecimal technicalPrice =
                context.calculatePriceStatistics(NVDA, Timeframe.LAST_24H).latestPrice();

            assertThat(technicalPrice)
                .as("the Technical agent's \"Current\" must be the same bar the Orchestrator prices")
                .isEqualByComparingTo(orchestratorPrice);
            assertThat(orchestratorPrice)
                .as("and that bar is the newest one available, not an older one")
                .isEqualByComparingTo(new BigDecimal("185.66"));
        }

        @Test
        @DisplayName("across every timeframe, including ones with no bars of their own")
        void agreesAcrossAllTimeframes() {
            MarketDataContext context = contextOf(Map.of(
                Timeframe.LAST_24H, List.of(FEB_02),
                Timeframe.LAST_7D, List.of(JAN_29, JAN_30),
                Timeframe.LAST_30D, List.of(JAN_29, JAN_30)
            ));

            BigDecimal latest = context.getLatestPrice(NVDA).orElseThrow();

            for (Timeframe timeframe : Timeframe.values()) {
                PriceStatistics stats = context.calculatePriceStatistics(NVDA, timeframe);
                if (stats.hasData()) {
                    assertThat(stats.latestPrice())
                        .as("%s must not report a different current price", timeframe)
                        .isEqualByComparingTo(latest);
                }
            }
        }

        @Test
        @DisplayName("when news events share the bucket with the single price bar")
        void newsInTheBucketDoesNotDisplaceTheLatestBar() {
            MarketDataContext context = contextOf(Map.of(
                Timeframe.LAST_24H, List.of(
                    news("NVDA", "2026-02-02T09:00:00Z", "Nvidia rallies"),
                    FEB_02,
                    news("NVDA", "2026-02-02T11:00:00Z", "Analyst raises target")),
                Timeframe.LAST_7D, List.of(JAN_29, JAN_30)
            ));

            assertThat(context.calculatePriceStatistics(NVDA, Timeframe.LAST_24H).latestPrice())
                .isEqualByComparingTo(context.getLatestPrice(NVDA).orElseThrow())
                .isEqualByComparingTo(new BigDecimal("185.66"));
        }
    }

    @Nested
    @DisplayName("Day-over-day change stays meaningful")
    class DayOverDayChange {

        @Test
        @DisplayName("a single bar in the window still yields a real change, not a flat 0.00%")
        void singleBarWindowReportsRealChange() {
            MarketDataContext context = contextOf(Map.of(
                Timeframe.LAST_24H, List.of(FEB_02),
                Timeframe.LAST_7D, List.of(JAN_29, JAN_30, FEB_02)
            ));

            PriceStatistics stats = context.calculatePriceStatistics(NVDA, Timeframe.LAST_24H);

            // 191.13 -> 185.66 is -2.86%; a flat 0.00% would make "buy on an up-day" unsatisfiable.
            assertThat(stats.priceChangePercent()).isNotNull();
            assertThat(stats.priceChangePercent().doubleValue()).isCloseTo(-2.86, org.assertj.core.data.Offset.offset(0.05));
            assertThat(stats.latestPrice()).isEqualByComparingTo(new BigDecimal("185.66"));
        }

        @Test
        @DisplayName("compares against the immediately preceding bar, not an older one")
        void comparesAgainstThePrecedingBar() {
            MarketDataContext context = contextOf(Map.of(
                Timeframe.LAST_24H, List.of(FEB_02),
                Timeframe.LAST_7D, List.of(JAN_29, JAN_30, FEB_02)
            ));

            PriceStatistics stats = context.calculatePriceStatistics(NVDA, Timeframe.LAST_24H);

            // The pair must be (Jan 30, Feb 2). Reaching back to Jan 29 (192.49) would widen the
            // range and misstate momentum.
            assertThat(stats.earliestPrice()).isEqualByComparingTo(new BigDecimal("191.13"));
            assertThat(stats.maxPrice()).isEqualByComparingTo(new BigDecimal("191.13"));
        }

        @Test
        @DisplayName("a window that already holds two or more bars is left alone")
        void wellPopulatedWindowIsUnchanged() {
            // Crypto shape: daily bars land on the evaluation boundary, so the 24h window has two.
            MarketDataContext context = contextOf(Map.of(
                Timeframe.LAST_24H, List.of(JAN_30, FEB_02),
                Timeframe.LAST_7D, List.of(JAN_29, JAN_30, FEB_02)
            ));

            PriceStatistics stats = context.calculatePriceStatistics(NVDA, Timeframe.LAST_24H);

            assertThat(stats.dataPointCount()).isEqualTo(2);
            assertThat(stats.earliestPrice()).isEqualByComparingTo(new BigDecimal("191.13"));
            assertThat(stats.latestPrice()).isEqualByComparingTo(new BigDecimal("185.66"));
        }

        @Test
        @DisplayName("a symbol with only one bar anywhere degrades gracefully")
        void singleBarOverallIsNotAnError() {
            MarketDataContext context = contextOf(Map.of(Timeframe.LAST_24H, List.of(FEB_02)));

            PriceStatistics stats = context.calculatePriceStatistics(NVDA, Timeframe.LAST_24H);

            assertThat(stats.hasData()).isTrue();
            assertThat(stats.latestPrice()).isEqualByComparingTo(new BigDecimal("185.66"));
            assertThat(context.getLatestPrice(NVDA).orElseThrow())
                .isEqualByComparingTo(new BigDecimal("185.66"));
        }
    }
}
