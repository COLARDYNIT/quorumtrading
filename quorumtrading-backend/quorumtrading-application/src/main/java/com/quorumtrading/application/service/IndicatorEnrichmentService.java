package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.HistoricalPricePort;
import com.quorumtrading.model.valueobject.BollingerBands;
import com.quorumtrading.model.valueobject.IndicatorContext;
import com.quorumtrading.model.valueobject.IndicatorSnapshot;
import com.quorumtrading.model.valueobject.IndicatorType;
import com.quorumtrading.model.valueobject.IndicatorValue;
import com.quorumtrading.model.valueobject.MACD;
import com.quorumtrading.model.valueobject.MovingAverage;
import com.quorumtrading.model.valueobject.PriceBar;
import com.quorumtrading.model.valueobject.RSI;
import com.quorumtrading.model.valueobject.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Application service for enriching trading decisions with technical indicators.
 *
 * <p><b>Hexagonal Architecture:</b> Application Layer Service</p>
 * <p>Orchestrates price fetching via ports and indicator calculation via domain service.
 * No @Transactional annotation - transactions belong in persistence adapters only.</p>
 *
 * <p><b>DDD Classification:</b> Application Service</p>
 * <p>Coordinates domain services and ports to provide indicator calculation use case.</p>
 *
 * <p><b>Indicator Requirements:</b></p>
 * <ul>
 *   <li>SMA-50: needs 50 days of data</li>
 *   <li>SMA-200: needs 200 days of data</li>
 *   <li>RSI-14: needs 15 days of data (14 periods + 1 for first change)</li>
 *   <li>MACD (12,26,9): needs 35 days of data (26 + 9)</li>
 *   <li>Bollinger Bands (20,2): needs 20 days of data</li>
 * </ul>
 *
 * @see IndicatorCalculationService
 * @see HistoricalPricePort
 */
public class IndicatorEnrichmentService {

    private static final Logger LOG = LoggerFactory.getLogger(IndicatorEnrichmentService.class);

    /** Period for short-term moving average. */
    private static final int SMA_SHORT_PERIOD = 50;

    /** Period for long-term moving average. */
    private static final int SMA_LONG_PERIOD = 200;

    /**
     * Number of trading days of historical data fetched per ticker.
     *
     * <p>Sized for the longest EMA, not the longest SMA. SMA-200 needs exactly 200 bars, but
     * EMA-200 is seeded from the SMA of its first 200 bars and only becomes an EMA over the
     * bars that follow — fetching exactly 200 made EMA-200 identical to SMA-200 for every
     * ticker on every run. The extra bars are the smoothing headroom.</p>
     *
     * <p>SMA still reads the most recent {@code period} bars, so widening the window does not
     * shift any SMA value.</p>
     *
     * <p>Kept at 240 rather than wider so the calendar-day backfill derived from it
     * (trading days * 7/5 + holiday buffer) stays under the 365-day auto-backfill cap —
     * above that the requirement would be silently truncated instead of met.</p>
     */
    public static final int REQUIRED_TRADING_DAYS =
        SMA_LONG_PERIOD + IndicatorCalculationService.minimumSmoothingBars(SMA_LONG_PERIOD) + 20;

    /** Period for RSI calculation. */
    private static final int RSI_PERIOD = 14;

    /** Fast EMA period for MACD. */
    private static final int MACD_FAST_PERIOD = 12;

    /** Slow EMA period for MACD. */
    private static final int MACD_SLOW_PERIOD = 26;

    /** Signal line period for MACD. */
    private static final int MACD_SIGNAL_PERIOD = 9;

    /** Period for Bollinger Bands SMA. */
    private static final int BOLLINGER_PERIOD = 20;

    /** Standard deviation multiplier for Bollinger Bands. */
    private static final BigDecimal BOLLINGER_MULTIPLIER = new BigDecimal("2.0");

    /** Cache TTL: indicators for a given ticker are valid for 5 minutes. */
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    private final HistoricalPricePort historicalPricePort;
    private final IndicatorCalculationService calculationService;

    /** Thread-safe cache: ticker → (snapshot, timestamp). Avoids redundant DB fetches + recalculation. */
    private final ConcurrentHashMap<String, CachedSnapshot> snapshotCache = new ConcurrentHashMap<>();

    private record CachedSnapshot(IndicatorSnapshot snapshot, long cachedAtMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAtMillis > CACHE_TTL_MS;
        }
    }

    /**
     * Creates an IndicatorEnrichmentService with required dependencies.
     *
     * @param historicalPricePort Port for fetching historical price data
     * @param calculationService Service for performing indicator calculations
     */
    public IndicatorEnrichmentService(
        HistoricalPricePort historicalPricePort,
        IndicatorCalculationService calculationService
    ) {
        this.historicalPricePort = historicalPricePort;
        this.calculationService = calculationService;
    }

    /**
     * Calculate all technical indicators for a list of stock tickers using current prices.
     * Suitable for live trading and interactive tools (chat, MCP, WebSocket broadcast).
     *
     * <p>Each ticker is processed independently - failures for one ticker
     * do not affect calculations for other tickers.</p>
     *
     * @param stockTickers List of stock symbols to calculate indicators for (null treated as empty)
     * @return IndicatorContext containing calculated indicators and any warnings
     */
    public IndicatorContext calculateIndicators(List<Symbol> stockTickers) {
        return calculateIndicators(stockTickers, Instant.now());
    }

    /**
     * Calculate all technical indicators for a list of stock tickers as-of a specific point in time.
     * During backtests, {@code asOf} should be the simulated analysis timestamp so that indicators
     * are computed from historical prices up to that date (not from today's prices).
     *
     * @param stockTickers List of stock symbols to calculate indicators for (null treated as empty)
     * @param asOf Upper bound for price data — only prices at or before this timestamp are used
     * @return IndicatorContext containing calculated indicators and any warnings
     */
    public IndicatorContext calculateIndicators(List<Symbol> stockTickers, Instant asOf) {
        if (stockTickers == null || stockTickers.isEmpty()) {
            LOG.debug("No stock tickers provided, returning empty context");
            return IndicatorContext.empty();
        }

        LOG.info("Calculating indicators for {} tickers as-of {}: {}",
            stockTickers.size(), asOf,
            stockTickers.stream().map(Symbol::canonical).collect(Collectors.joining(", ")));

        Map<Symbol, IndicatorSnapshot> snapshots = new HashMap<>();

        for (Symbol ticker : stockTickers) {
            try {
                long start = System.nanoTime();
                IndicatorSnapshot snapshot = calculateForTicker(ticker, asOf);
                long ms = (System.nanoTime() - start) / 1_000_000;
                LOG.info("Indicators for {} calculated in {}ms (asOf={})", ticker.canonical(), ms, asOf);
                snapshots.put(ticker, snapshot);
            } catch (Exception e) {
                LOG.error("Unexpected error calculating indicators for {}: {}",
                    ticker.canonical(), e.getMessage(), e);
                snapshots.put(ticker, IndicatorSnapshot.empty(ticker,
                    "Unexpected error: " + e.getMessage()));
            }
        }

        LOG.info("Completed indicator calculation for {} tickers", snapshots.size());
        return new IndicatorContext(snapshots);
    }

    /**
     * Calculate all indicators for a single ticker as-of a specific point in time.
     *
     * @param ticker The stock symbol to calculate indicators for
     * @param asOf Upper bound for price data — only prices at or before this timestamp are used
     * @return IndicatorSnapshot with calculated values and any warnings
     */
    private IndicatorSnapshot calculateForTicker(Symbol ticker, Instant asOf) {
        // Cache key includes date (truncated to day) to avoid cross-contamination between
        // backtest dates and live queries. Same-day queries still benefit from the cache.
        // The symbol half must be canonical(), not ticker(): the bare root is not an identity.
        // Keying on the root made SAP:XETR and SAP collide on one entry, so whichever was asked
        // for first populated it and the other silently received that instrument's prices under
        // its own name — call-order dependent, and invisible to the caller. The separator is '@'
        // rather than ':' because canonical() already uses ':' to carry the MIC.
        String dateKey = asOf.truncatedTo(java.time.temporal.ChronoUnit.DAYS).toString();
        String cacheKey = ticker.canonical() + "@" + dateKey;
        CachedSnapshot cached = snapshotCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            LOG.debug("Indicator cache hit for {} (age {}ms)", cacheKey,
                System.currentTimeMillis() - cached.cachedAtMillis());
            return cached.snapshot();
        }

        LOG.debug("Fetching {} days of historical prices for {} as-of {}",
            REQUIRED_TRADING_DAYS, ticker.canonical(), asOf);

        long t0 = System.nanoTime();

        List<PriceBar> priceBars;
        try {
            priceBars = historicalPricePort.getHistoricalPrices(ticker, REQUIRED_TRADING_DAYS, asOf);
        } catch (Exception e) {
            LOG.warn("Failed to fetch prices for {}: {}", ticker.canonical(), e.getMessage());
            return IndicatorSnapshot.empty(ticker,
                "Failed to fetch price data: " + e.getMessage());
        }

        long t1 = System.nanoTime();

        if (priceBars == null || priceBars.isEmpty()) {
            LOG.warn("No price data available for {} as-of {}", ticker.canonical(), asOf);
            return IndicatorSnapshot.empty(ticker, "No price data available");
        }

        LOG.debug("Received {} price bars for {}", priceBars.size(), ticker.canonical());

        // Extract closing prices from price bars (most recent last)
        List<BigDecimal> closePrices = priceBars.stream()
            .map(PriceBar::close)
            .toList();

        long t2 = System.nanoTime();

        // Calculate each indicator independently
        Map<IndicatorType, IndicatorValue> values = new EnumMap<>(IndicatorType.class);
        Map<IndicatorType, String> warnings = new EnumMap<>(IndicatorType.class);
        Instant calculatedAt = asOf;

        calculateMovingAverages(closePrices, calculatedAt, values, warnings);
        long t3 = System.nanoTime();

        calculateRSI(closePrices, calculatedAt, values, warnings);
        long t4 = System.nanoTime();

        calculateMACD(closePrices, calculatedAt, values, warnings);
        long t5 = System.nanoTime();

        calculateBollingerBands(closePrices, calculatedAt, values, warnings);
        long t6 = System.nanoTime();

        LOG.info("Indicator timing for {} ({} bars, asOf={}): dbFetch={}ms extract={}ms sma={}ms rsi={}ms macd={}ms bollinger={}ms",
            ticker.canonical(), priceBars.size(), asOf,
            (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000,
            (t3 - t2) / 1_000_000, (t4 - t3) / 1_000_000,
            (t5 - t4) / 1_000_000, (t6 - t5) / 1_000_000);

        IndicatorSnapshot snapshot = new IndicatorSnapshot(ticker, calculatedAt, values, warnings);

        // Cache the result
        snapshotCache.put(cacheKey, new CachedSnapshot(snapshot, System.currentTimeMillis()));

        return snapshot;
    }

    /**
     * Calculate Simple and Exponential Moving Averages (SMA-50/200, EMA-50/200).
     */
    private void calculateMovingAverages(
        List<BigDecimal> prices,
        Instant calculatedAt,
        Map<IndicatorType, IndicatorValue> values,
        Map<IndicatorType, String> warnings
    ) {
        try {
            BigDecimal sma50 = null;
            BigDecimal sma200 = null;
            BigDecimal ema50 = null;
            BigDecimal ema200 = null;
            StringBuilder warningMsg = new StringBuilder();

            // Calculate SMA-50
            Optional<MovingAverage> sma50Opt = calculationService.calculateSMA(
                prices, SMA_SHORT_PERIOD, calculatedAt);
            if (sma50Opt.isPresent()) {
                sma50 = sma50Opt.get().value();
            } else {
                warningMsg.append("Insufficient data for SMA-50 (need ")
                    .append(SMA_SHORT_PERIOD).append(" days, have ")
                    .append(prices.size()).append(")");
            }

            // Calculate SMA-200
            Optional<MovingAverage> sma200Opt = calculationService.calculateSMA(
                prices, SMA_LONG_PERIOD, calculatedAt);
            if (sma200Opt.isPresent()) {
                sma200 = sma200Opt.get().value();
            } else {
                if (!warningMsg.isEmpty()) {
                    warningMsg.append("; ");
                }
                warningMsg.append("Insufficient data for SMA-200 (need ")
                    .append(SMA_LONG_PERIOD).append(" days, have ")
                    .append(prices.size()).append(")");
            }

            // Calculate EMA-50
            Optional<MovingAverage> ema50Opt = calculationService.calculateEMAIndicator(
                prices, SMA_SHORT_PERIOD, calculatedAt);
            if (ema50Opt.isPresent()) {
                ema50 = ema50Opt.get().value();
            }

            // Calculate EMA-200
            Optional<MovingAverage> ema200Opt = calculationService.calculateEMAIndicator(
                prices, SMA_LONG_PERIOD, calculatedAt);
            if (ema200Opt.isPresent()) {
                ema200 = ema200Opt.get().value();
            }

            // Only add to values if at least one MA was calculated
            if (sma50 != null || sma200 != null || ema50 != null || ema200 != null) {
                values.put(IndicatorType.MOVING_AVERAGE,
                    new IndicatorValue.MovingAverageValue(sma50, sma200, ema50, ema200));
            }

            // Add warning if SMA-200 couldn't be calculated but SMA-50 could
            if (sma50 != null && sma200 == null) {
                warnings.put(IndicatorType.MOVING_AVERAGE,
                    "Insufficient data for SMA-200 (need " + SMA_LONG_PERIOD +
                    " days, have " + prices.size() + ")");
            } else if (sma50 == null) {
                warnings.put(IndicatorType.MOVING_AVERAGE, warningMsg.toString());
            }

        } catch (Exception e) {
            LOG.warn("Error calculating moving averages: {}", e.getMessage());
            warnings.put(IndicatorType.MOVING_AVERAGE,
                "Calculation error: " + e.getMessage());
        }
    }

    /**
     * Calculate Relative Strength Index (RSI-14).
     */
    private void calculateRSI(
        List<BigDecimal> prices,
        Instant calculatedAt,
        Map<IndicatorType, IndicatorValue> values,
        Map<IndicatorType, String> warnings
    ) {
        try {
            Optional<RSI> rsiOpt = calculationService.calculateRSI(
                prices, RSI_PERIOD, calculatedAt);

            if (rsiOpt.isPresent()) {
                values.put(IndicatorType.RSI,
                    new IndicatorValue.RSIValue(rsiOpt.get().value()));
            } else {
                warnings.put(IndicatorType.RSI,
                    "Insufficient data for RSI-14 (need " + (RSI_PERIOD + 1) +
                    " days, have " + prices.size() + ")");
            }
        } catch (Exception e) {
            LOG.warn("Error calculating RSI: {}", e.getMessage());
            warnings.put(IndicatorType.RSI, "Calculation error: " + e.getMessage());
        }
    }

    /**
     * Calculate MACD (12, 26, 9).
     */
    private void calculateMACD(
        List<BigDecimal> prices,
        Instant calculatedAt,
        Map<IndicatorType, IndicatorValue> values,
        Map<IndicatorType, String> warnings
    ) {
        try {
            Optional<MACD> macdOpt = calculationService.calculateMACD(
                prices, MACD_FAST_PERIOD, MACD_SLOW_PERIOD, MACD_SIGNAL_PERIOD, calculatedAt);

            if (macdOpt.isPresent()) {
                MACD macd = macdOpt.get();
                values.put(IndicatorType.MACD,
                    new IndicatorValue.MACDValue(
                        macd.macdLine(),
                        macd.signalLine(),
                        macd.histogram()));
            } else {
                int requiredDays = MACD_SLOW_PERIOD + MACD_SIGNAL_PERIOD;
                warnings.put(IndicatorType.MACD,
                    "Insufficient data for MACD (need " + requiredDays +
                    " days, have " + prices.size() + ")");
            }
        } catch (Exception e) {
            LOG.warn("Error calculating MACD: {}", e.getMessage());
            warnings.put(IndicatorType.MACD, "Calculation error: " + e.getMessage());
        }
    }

    /**
     * Calculate Bollinger Bands (20, 2).
     */
    private void calculateBollingerBands(
        List<BigDecimal> prices,
        Instant calculatedAt,
        Map<IndicatorType, IndicatorValue> values,
        Map<IndicatorType, String> warnings
    ) {
        try {
            Optional<BollingerBands> bbOpt = calculationService.calculateBollingerBands(
                prices, BOLLINGER_PERIOD, BOLLINGER_MULTIPLIER, calculatedAt);

            if (bbOpt.isPresent()) {
                BollingerBands bb = bbOpt.get();
                values.put(IndicatorType.BOLLINGER_BANDS,
                    new IndicatorValue.BollingerValue(
                        bb.upperBand(),
                        bb.middleBand(),
                        bb.lowerBand()));
            } else {
                warnings.put(IndicatorType.BOLLINGER_BANDS,
                    "Insufficient data for Bollinger Bands (need " + BOLLINGER_PERIOD +
                    " days, have " + prices.size() + ")");
            }
        } catch (Exception e) {
            LOG.warn("Error calculating Bollinger Bands: {}", e.getMessage());
            warnings.put(IndicatorType.BOLLINGER_BANDS,
                "Calculation error: " + e.getMessage());
        }
    }
}
