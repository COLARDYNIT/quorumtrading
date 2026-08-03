package com.quorumtrading.application.service;

import com.quorumtrading.model.valueobject.BollingerBands;
import com.quorumtrading.model.valueobject.MACD;
import com.quorumtrading.model.valueobject.MovingAverage;
import com.quorumtrading.model.valueobject.RSI;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Application service for calculating technical indicators from price data.
 *
 * <p><b>Hexagonal Architecture:</b> Application Layer Service</p>
 * <p>This service contains pure calculation logic and has no framework dependencies.
 * It accepts domain primitives (prices) and returns domain value objects (indicators).</p>
 *
 * <p><b>DDD Classification:</b> Application Service</p>
 */
public class IndicatorCalculationService {

    /**
     * Precision used for intermediate arithmetic.
     *
     * <p><b>Never round inside a calculation to a scale chosen for display.</b> These
     * indicators were computed entirely at two decimal places, which is fine for a
     * $180 share and destroys the signal for anything cheap. On a coin trading near
     * $0.16 the average daily loss is around $0.003, which rounds to {@code 0.00} —
     * and RSI reads that as "no losses at all" and returns exactly 100. Every
     * low-priced asset therefore reported a permanently pinned RSI-14 of 100.00,
     * which no strategy threshold can ever fire against. The same rounding drove
     * Bollinger variance to zero, collapsing the bands onto the middle line and
     * making every band-based rule unreachable.</p>
     *
     * <p>Intermediates now carry far more precision than any output needs, and
     * rounding happens once, at the end, at a scale chosen for the value's
     * magnitude.</p>
     */
    private static final int CALC_SCALE = 16;

    /** Display scale for values at or above 1.0 — unchanged from the original behaviour. */
    private static final int SCALE = 2;
    private static final int MACD_SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * Chooses an output scale that preserves meaning at the value's magnitude.
     *
     * <p>Two decimals is right for a share priced in dollars and useless for one
     * priced in thousandths: rounding a Bollinger band of 0.1683 to 0.17 discards
     * the very difference the band exists to express. Anything at or above 1.0
     * keeps the original two decimals, so every existing equity output is
     * byte-identical; smaller values get the extra places they need.</p>
     *
     * @param reference a representative value (typically the price level involved)
     * @param minScale  a floor, so callers with their own minimum keep it
     */
    static int outputScaleFor(BigDecimal reference, int minScale) {
        BigDecimal abs = reference == null ? BigDecimal.ONE : reference.abs();
        int scale;
        if (abs.compareTo(BigDecimal.ONE) >= 0) {
            scale = SCALE;
        } else if (abs.compareTo(new BigDecimal("0.01")) >= 0) {
            scale = 6;
        } else if (abs.compareTo(new BigDecimal("0.0001")) >= 0) {
            scale = 8;
        } else {
            scale = 10;
        }
        return Math.max(scale, minScale);
    }

    /**
     * Bars required beyond {@code period} before an EMA is considered meaningful.
     *
     * <p>With zero smoothing bars an EMA is exactly its SMA seed. How many bars it takes to
     * shed that seed scales with the period, because the smoothing factor {@code k = 2/(p+1)}
     * shrinks as the period grows: a 3-period EMA is dominated by recent prices within a
     * handful of bars, while a 200-period EMA barely moves over the same span. One tenth of
     * the period is the headroom at which the two visibly diverge.</p>
     *
     * @param period the EMA period
     * @return minimum number of bars required beyond the period
     */
    /**
     * The smallest non-zero magnitude among the supplied values, used to pick an
     * output scale that keeps every component meaningful. Falls back to the first
     * value when all are zero.
     */
    private static BigDecimal smallestNonZero(BigDecimal... values) {
        BigDecimal smallest = null;
        for (BigDecimal value : values) {
            if (value == null || value.signum() == 0) {
                continue;
            }
            BigDecimal abs = value.abs();
            if (smallest == null || abs.compareTo(smallest) < 0) {
                smallest = abs;
            }
        }
        return smallest != null ? smallest
            : (values.length > 0 && values[0] != null ? values[0] : BigDecimal.ONE);
    }

    static int minimumSmoothingBars(int period) {
        return Math.max(1, period / 10);
    }

    /**
     * Calculate Simple Moving Average.
     *
     * @param prices Historical close prices (most recent last)
     * @param period MA period (e.g., 50, 200)
     * @param calculatedAt Timestamp for the indicator
     * @return Optional MovingAverage if sufficient data, empty otherwise
     */
    public Optional<MovingAverage> calculateSMA(
        List<BigDecimal> prices,
        int period,
        Instant calculatedAt
    ) {
        if (prices == null || prices.size() < period) {
            return Optional.empty();
        }

        // Extract last 'period' prices
        List<BigDecimal> relevantPrices = prices.subList(prices.size() - period, prices.size());

        // Calculate sum
        BigDecimal sum = relevantPrices.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Divide at full precision, then round once for presentation at a scale that
        // survives the price level — a sub-dollar asset needs more than two decimals
        // before the average stops distinguishing anything.
        BigDecimal average = sum.divide(BigDecimal.valueOf(period), CALC_SCALE, ROUNDING);
        average = average.setScale(outputScaleFor(average, 0), ROUNDING);

        return Optional.of(new MovingAverage(average, period, calculatedAt));
    }

    /**
     * Calculate Relative Strength Index.
     *
     * Algorithm:
     * 1. Calculate price changes
     * 2. Separate gains and losses
     * 3. Calculate average gain and average loss over period
     * 4. RS = avgGain / avgLoss
     * 5. RSI = 100 - (100 / (1 + RS))
     *
     * @param prices Historical close prices (most recent last)
     * @param period RSI period (typically 14)
     * @param calculatedAt Timestamp for the indicator
     * @return Optional RSI if sufficient data, empty otherwise
     */
    public Optional<RSI> calculateRSI(
        List<BigDecimal> prices,
        int period,
        Instant calculatedAt
    ) {
        // Need period + 1 prices to calculate period changes
        if (prices == null || prices.size() < period + 1) {
            return Optional.empty();
        }

        // Calculate the LAST period price changes
        // We need to look at the last 'period' changes, which requires period+1 prices
        List<BigDecimal> changes = new ArrayList<>();
        int startIdx = prices.size() - period; // Start from the price that gives us 'period' changes
        for (int i = startIdx; i < prices.size(); i++) {
            BigDecimal change = prices.get(i).subtract(prices.get(i - 1));
            changes.add(change);
        }

        // Separate gains and losses
        BigDecimal totalGain = BigDecimal.ZERO;
        BigDecimal totalLoss = BigDecimal.ZERO;

        for (BigDecimal change : changes) {
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                totalGain = totalGain.add(change);
            } else if (change.compareTo(BigDecimal.ZERO) < 0) {
                totalLoss = totalLoss.add(change.abs());
            }
        }

        // Average at full precision. Rounding here is what pinned RSI at 100 for every
        // cheap asset: a real average loss of 0.003 became 0.00, and the zero-loss branch
        // below then reported "no losses at all".
        BigDecimal avgGain = totalGain.divide(BigDecimal.valueOf(period), CALC_SCALE, ROUNDING);
        BigDecimal avgLoss = totalLoss.divide(BigDecimal.valueOf(period), CALC_SCALE, ROUNDING);

        // Calculate RSI. The boundary cases are now reached only by genuinely
        // unbroken runs, not by arithmetic.
        BigDecimal rsi;
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0 && avgGain.compareTo(BigDecimal.ZERO) == 0) {
            // Perfectly flat window: no gains and no losses. RSI is undefined by the
            // ratio, and 50 is the neutral reading that says exactly that — reporting
            // 100 here would flag a motionless price as maximally overbought.
            rsi = new BigDecimal("50.00");
        } else if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            rsi = new BigDecimal("100.00");
        } else if (avgGain.compareTo(BigDecimal.ZERO) == 0) {
            rsi = BigDecimal.ZERO.setScale(2);
        } else {
            BigDecimal rs = avgGain.divide(avgLoss, CALC_SCALE, ROUNDING);
            rsi = new BigDecimal("100").subtract(
                new BigDecimal("100").divide(BigDecimal.ONE.add(rs), CALC_SCALE, ROUNDING)
            );
        }

        // RSI is bounded 0-100, so two decimals is meaningful at every price level.
        return Optional.of(new RSI(rsi.setScale(SCALE, ROUNDING), calculatedAt));
    }

    /**
     * Calculate MACD (Moving Average Convergence Divergence).
     *
     * Algorithm:
     * 1. Calculate fast EMA (typically 12-period)
     * 2. Calculate slow EMA (typically 26-period)
     * 3. MACD Line = Fast EMA - Slow EMA
     * 4. Signal Line = 9-period EMA of MACD Line
     * 5. Histogram = MACD Line - Signal Line
     *
     * @param prices Historical close prices (most recent last)
     * @param fastPeriod Fast EMA period (default: 12)
     * @param slowPeriod Slow EMA period (default: 26)
     * @param signalPeriod Signal line EMA period (default: 9)
     * @param calculatedAt Timestamp for the indicator
     * @return Optional MACD if sufficient data, empty otherwise
     */
    public Optional<MACD> calculateMACD(
        List<BigDecimal> prices,
        int fastPeriod,
        int slowPeriod,
        int signalPeriod,
        Instant calculatedAt
    ) {
        // Need at least slowPeriod + signalPeriod prices
        if (prices == null || prices.size() < slowPeriod + signalPeriod) {
            return Optional.empty();
        }

        // Build rolling EMA arrays in a single O(N) pass each
        List<BigDecimal> fastEMAs = calculateEMASeries(prices, fastPeriod);
        List<BigDecimal> slowEMAs = calculateEMASeries(prices, slowPeriod);

        // MACD Line values = Fast EMA - Slow EMA (from slowPeriod onward, where both exist)
        List<BigDecimal> macdValues = new ArrayList<>();
        int slowOffset = slowPeriod - 1; // index of first slow EMA value
        int fastOffset = fastPeriod - 1; // index of first fast EMA value
        for (int i = slowOffset; i < prices.size(); i++) {
            BigDecimal fastVal = fastEMAs.get(i - fastOffset);
            BigDecimal slowVal = slowEMAs.get(i - slowOffset);
            macdValues.add(fastVal.subtract(slowVal));
        }

        // Current MACD line is the last value
        BigDecimal macdLine = macdValues.getLast();

        // Signal Line = EMA of MACD line values
        BigDecimal signalLine = calculateEMA(macdValues, signalPeriod);

        // Histogram = MACD Line - Signal Line
        BigDecimal histogram = macdLine.subtract(signalLine);

        // MACD is a difference of moving averages, so it is orders of magnitude smaller
        // than the prices behind it — and it was the one indicator still rounded at a
        // fixed four decimals while the others moved to a magnitude-aware scale. On a
        // coin near $0.07 the histogram came out as 0.0002, a single significant figure,
        // and on one near $0.0000044 all three components rounded to a flat 0.0000. Any
        // strategy threshold written against them was unreachable.
        //
        // The scale is taken from the SMALLEST non-zero component: the histogram is
        // typically far smaller than the line, and sizing on the line alone would
        // annihilate exactly the value most rules are written against.
        int macdScale = outputScaleFor(smallestNonZero(macdLine, signalLine, histogram), MACD_SCALE);

        return Optional.of(new MACD(
            macdLine.setScale(macdScale, ROUNDING),
            signalLine.setScale(macdScale, ROUNDING),
            histogram.setScale(macdScale, ROUNDING),
            calculatedAt
        ));
    }

    /**
     * Calculate Bollinger Bands.
     *
     * Algorithm:
     * 1. Middle Band = SMA(period)
     * 2. Calculate standard deviation of prices over period
     * 3. Upper Band = Middle Band + (stdDev × multiplier)
     * 4. Lower Band = Middle Band - (stdDev × multiplier)
     *
     * @param prices Historical close prices (most recent last)
     * @param period Bollinger Bands period (default: 20)
     * @param stdDevMultiplier Standard deviation multiplier (default: 2.0)
     * @param calculatedAt Timestamp for the indicator
     * @return Optional BollingerBands if sufficient data, empty otherwise
     */
    public Optional<BollingerBands> calculateBollingerBands(
        List<BigDecimal> prices,
        int period,
        BigDecimal stdDevMultiplier,
        Instant calculatedAt
    ) {
        if (prices == null || prices.size() < period) {
            return Optional.empty();
        }

        // Calculate middle band (SMA)
        Optional<MovingAverage> smaOpt = calculateSMA(prices, period, calculatedAt);
        if (smaOpt.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal middleBand = smaOpt.get().value();

        // Extract relevant prices
        List<BigDecimal> relevantPrices = prices.subList(prices.size() - period, prices.size());

        // Calculate standard deviation
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal price : relevantPrices) {
            BigDecimal diff = price.subtract(middleBand);
            variance = variance.add(diff.multiply(diff));
        }
        // Variance is a squared quantity, so it is far smaller than the prices it came
        // from — for a coin near $0.16 it lands around 0.000004, which at two decimals
        // is zero. That produced a zero standard deviation, collapsed both bands onto
        // the middle line, and made every band-based rule permanently unreachable.
        variance = variance.divide(BigDecimal.valueOf(period), CALC_SCALE, ROUNDING);

        // Standard deviation = sqrt(variance)
        BigDecimal stdDev = sqrt(variance, CALC_SCALE);

        // Calculate bands, rounding once at a scale the price level can carry.
        BigDecimal bandWidth = stdDev.multiply(stdDevMultiplier);
        int bandScale = outputScaleFor(middleBand, middleBand.scale());
        BigDecimal upperBand = middleBand.add(bandWidth).setScale(bandScale, ROUNDING);
        BigDecimal lowerBand = middleBand.subtract(bandWidth).setScale(bandScale, ROUNDING);

        return Optional.of(new BollingerBands(
            upperBand,
            middleBand,
            lowerBand,
            calculatedAt
        ));
    }

    /**
     * Calculate Exponential Moving Average (final value).
     *
     * <p>EMA weights recent prices more heavily than SMA, making it more responsive
     * to recent price changes. This is useful for trend-following strategies that need
     * faster recovery after bear markets.</p>
     *
     * <p>Requires {@link #minimumSmoothingBars(int)} bars BEYOND the period. The EMA is seeded
     * with the SMA of the first {@code period} bars and only becomes an EMA as the smoothing
     * loop runs over the bars after it. Given exactly {@code period} bars the loop never
     * executes and the result is byte-identical to the SMA — which is how EMA-200 and SMA-200
     * came to be reported as the same number for every ticker on every run.</p>
     *
     * @param prices Historical close prices (most recent last)
     * @param period EMA period (e.g., 50, 200)
     * @param calculatedAt Timestamp for the indicator
     * @return Optional MovingAverage if sufficient data, empty otherwise
     */
    public Optional<MovingAverage> calculateEMAIndicator(
        List<BigDecimal> prices,
        int period,
        Instant calculatedAt
    ) {
        if (prices == null || prices.size() < period + minimumSmoothingBars(period)) {
            return Optional.empty();
        }

        // The smoothing loop runs at full internal precision; round once here so the
        // reported value carries meaningful decimals rather than sixteen of them.
        //
        // Floor at four decimals rather than the two an SMA gets: an EMA's whole purpose
        // is to differ from the SMA it is seeded with, and on a slow-moving long period
        // that difference is a fraction of a cent. Rounding to two decimals erases it and
        // reproduces the old defect where EMA-200 and SMA-200 reported the same number.
        BigDecimal emaValue = calculateEMA(prices, period);
        emaValue = emaValue.setScale(outputScaleFor(emaValue, MACD_SCALE), ROUNDING);

        return Optional.of(new MovingAverage(emaValue, period, calculatedAt));
    }

    /**
     * Calculate all intermediate EMA values in a single O(N) pass.
     * Returns a list of EMA values starting from the first complete period.
     *
     * @param prices Price data (most recent last)
     * @param period EMA period
     * @return List of EMA values, one per data point from index (period-1) onward
     */
    private List<BigDecimal> calculateEMASeries(List<BigDecimal> prices, int period) {
        if (prices.size() < period) {
            throw new IllegalArgumentException("Insufficient data for EMA series calculation");
        }

        BigDecimal k = new BigDecimal("2")
            .divide(BigDecimal.valueOf(period + 1), CALC_SCALE, ROUNDING);
        BigDecimal oneMinusK = BigDecimal.ONE.subtract(k);

        // First EMA = SMA of first period prices
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(prices.get(i));
        }
        BigDecimal ema = sum.divide(BigDecimal.valueOf(period), CALC_SCALE, ROUNDING);

        List<BigDecimal> series = new ArrayList<>(prices.size() - period + 1);
        series.add(ema);

        for (int i = period; i < prices.size(); i++) {
            ema = prices.get(i).multiply(k)
                .add(ema.multiply(oneMinusK))
                .setScale(CALC_SCALE, ROUNDING);
            series.add(ema);
        }

        return series;
    }

    /**
     * Calculate Exponential Moving Average (final value only).
     *
     * EMA formula:
     * EMA[today] = (Price[today] × K) + (EMA[yesterday] × (1 - K))
     * Where K = 2 / (period + 1)
     *
     * First EMA = SMA of first period prices
     */
    private BigDecimal calculateEMA(List<BigDecimal> prices, int period) {
        if (prices.size() < period) {
            throw new IllegalArgumentException("Insufficient data for EMA calculation");
        }

        // Calculate multiplier K
        BigDecimal k = new BigDecimal("2")
            .divide(BigDecimal.valueOf(period + 1), CALC_SCALE, ROUNDING);

        // First EMA = SMA of first period prices
        List<BigDecimal> initialPrices = prices.subList(0, period);
        BigDecimal sum = initialPrices.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ema = sum.divide(BigDecimal.valueOf(period), CALC_SCALE, ROUNDING);

        // Calculate EMA for remaining prices
        for (int i = period; i < prices.size(); i++) {
            BigDecimal price = prices.get(i);
            ema = price.multiply(k)
                .add(ema.multiply(BigDecimal.ONE.subtract(k)))
                .setScale(CALC_SCALE, ROUNDING);
        }

        return ema;
    }

    /**
     * Calculate square root using Newton's method.
     * Used for standard deviation calculation.
     */
    /**
     * Square root at the requested scale.
     *
     * <p>This was hand-rolled Newton iteration seeded at {@code value/2} and capped
     * at ten passes, which does not converge for the small variances a low-priced
     * asset produces. For a variance around 1.9e-11 the seed is far <em>below</em>
     * the root, so the first pass overshoots to roughly 1.0 and the remaining nine
     * merely halve it — landing on exactly 2⁻⁸ regardless of the input. That is how
     * a coin trading at $0.0000044 acquired a Bollinger half-width of 0.0039, about
     * nine hundred times its own price, and a <b>negative</b> lower band.</p>
     *
     * <p>{@link BigDecimal#sqrt(java.math.MathContext)} is correctly rounded and
     * scale-independent, so the failure cannot recur at any price level.</p>
     */
    private BigDecimal sqrt(BigDecimal value, int scale) {
        if (value.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return value.sqrt(new java.math.MathContext(Math.max(scale, 16)))
            .setScale(scale, ROUNDING);
    }
}
