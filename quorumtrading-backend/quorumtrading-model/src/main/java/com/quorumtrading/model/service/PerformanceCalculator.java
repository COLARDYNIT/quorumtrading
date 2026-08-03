package com.quorumtrading.model.service;

import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.valueobject.Money;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure domain service computing performance metrics for simulations that
 * receive cash contributions over time (recurring-investment plans).
 *
 * <p>Two metrics are surfaced together because they answer different questions
 * and either one in isolation can mislead:</p>
 *
 * <ul>
 *   <li><b>TWR (time-weighted return)</b> — geometric chain of sub-period
 *       returns broken at every cash flow. Cashflow timing is removed, so this
 *       reflects the strategy's skill independent of when the user happened
 *       to add cash. Industry-standard for comparing strategies.</li>
 *   <li><b>MWR (money-weighted return, a.k.a. IRR)</b> — the user's <em>actual</em>
 *       return given the cashflow timing. Solves
 *       Σ cashflow_i × (1 + r)^(years_remaining_i) − terminal_value = 0
 *       via Newton-Raphson. Reflects "what did <em>I</em> earn?".</li>
 * </ul>
 *
 * <p><b>Pure / stateless.</b> No CDI annotations. Used both as a domain helper
 * and from {@code SimulationResponse} mappers.</p>
 *
 * <p><b>Numerical robustness:</b> all internal arithmetic uses
 * {@link MathContext#DECIMAL64}. Returns are expressed as decimal fractions
 * (0.10 = +10%). For Newton-Raphson we cap iterations and tolerate
 * non-convergence by returning {@code null} rather than throwing — non-convergence
 * is meaningful (e.g. negative terminal value, contribution but zero growth) and
 * callers should display it as "n/a" rather than crash.</p>
 */
public final class PerformanceCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int IRR_MAX_ITERATIONS = 100;
    private static final BigDecimal IRR_TOLERANCE = new BigDecimal("0.0000001");
    private static final BigDecimal IRR_INITIAL_GUESS = new BigDecimal("0.10");
    /** Smallest absolute total invested below which performance metrics are undefined. */
    private static final BigDecimal MIN_BASIS_USD = new BigDecimal("0.01");

    /**
     * Shortest simulated horizon for which an annualised MWR is reported: 30 days.
     * Below this the annualisation factor dominates the result entirely.
     */
    private static final double MIN_MWR_HORIZON_YEARS = 30.0 / 365.25;

    private PerformanceCalculator() {}

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Total cash invested = initial cash + Σ contributions.
     */
    public static Money totalInvested(Money initialCash, List<Contribution> contributions) {
        Objects.requireNonNull(initialCash, "initialCash");
        Money total = initialCash;
        if (contributions != null) {
            for (Contribution c : contributions) {
                total = total.add(c.amount());
            }
        }
        return total;
    }

    /**
     * Contribution-aware simple return percentage:
     * {@code ((finalValue − totalInvested) / totalInvested) × 100}, where
     * {@code totalInvested = initialCash + Σ contributions}. A DCA top-up is
     * counted as invested capital, not profit; with no contributions this is
     * the plain return on the seed cash.
     *
     * <p>Returns {@code null} (undefined) when:</p>
     * <ul>
     *   <li>{@code initialCash} or {@code finalValue} is null, or</li>
     *   <li>total invested is not strictly positive (there is no meaningful
     *       basis to measure a return against).</li>
     * </ul>
     *
     * <p>The result carries scale 4 (e.g. {@code 12.3400} for +12.34%). Callers wanting two
     * decimals apply their own {@code setScale(2)}.</p>
     *
     * <p>The ×100 happens <em>before</em> the division, not after. The historical adapter-layer
     * formula divided at scale 4 and then multiplied, which rounded the ratio to four places and
     * so left only two meaningful decimals in the percentage — while still printing four. A run
     * worth 10294.74 on 10000 reported {@code returnPercent 2.9500} next to
     * {@code twrPercent 2.9474} in the same payload, advertising a precision it did not have and
     * making two fields that must agree look like they disagreed.</p>
     *
     * <p>Amounts are compared numerically; {@code finalValue}'s currency is
     * not validated against {@code initialCash}'s (all call sites operate in
     * a single currency).</p>
     *
     * @param initialCash    the simulation's seed cash
     * @param contributions  cash injected after start (null ⇒ none)
     * @param finalValue     the portfolio's current/final total value
     * @return the return percentage at scale 4, or {@code null} when undefined
     */
    public static BigDecimal simpleReturnPercent(Money initialCash,
                                                 List<Contribution> contributions,
                                                 Money finalValue) {
        if (initialCash == null || finalValue == null) {
            return null;
        }
        BigDecimal invested = totalInvested(initialCash, contributions).amount();
        if (invested.signum() <= 0) {
            return null;
        }
        return finalValue.amount()
                .subtract(invested)
                .multiply(ONE_HUNDRED)
                .divide(invested, 4, RoundingMode.HALF_UP);
    }

    /**
     * Time-weighted return as a decimal (0.10 = +10%).
     *
     * <p>Returns {@code null} when there is insufficient data:
     * fewer than two snapshots, or the first snapshot has non-positive value.</p>
     *
     * <p>Each contribution breaks the timeline into a sub-period. Within a
     * sub-period the return is (endValue / startValue) − 1. Cash injected
     * at the boundary is added to startValue of the next sub-period so it
     * doesn't pollute the period return.</p>
     */
    public static BigDecimal computeTwr(List<PortfolioSnapshot> snapshots,
                                        List<Contribution> contributions) {
        if (snapshots == null || snapshots.size() < 2) {
            return null;
        }
        List<PortfolioSnapshot> sorted = new ArrayList<>(snapshots);
        sorted.sort(Comparator.comparing(PortfolioSnapshot::getTimestamp));

        List<Contribution> sortedContribs = sortedContributions(contributions);

        // Geometric chain of (endValue / (startValue + contributions-attributed-to-this-period)) ratios.
        // Attribution convention: a contribution made in the half-open interval
        // [prev.timestamp, curr.timestamp) is added to the START of period [prev, curr).
        // That puts boundary contributions (snapshot taken pre-contribution, then contribution
        // recorded, then next snapshot) at the start of the *next* period, where they belong.
        BigDecimal growthFactor = ONE;
        BigDecimal firstValue = toBigDecimal(sorted.get(0).getTotalValue());
        if (firstValue == null || firstValue.signum() <= 0) {
            return null;
        }

        for (int i = 1; i < sorted.size(); i++) {
            PortfolioSnapshot prev = sorted.get(i - 1);
            PortfolioSnapshot curr = sorted.get(i);
            BigDecimal startValue = toBigDecimal(prev.getTotalValue());
            BigDecimal endValue = toBigDecimal(curr.getTotalValue());
            if (startValue == null || endValue == null || startValue.signum() <= 0) {
                continue;
            }

            BigDecimal contribInPeriod = contributionsInPeriod(sortedContribs,
                    prev.getTimestamp(), curr.getTimestamp());
            BigDecimal adjustedStart = startValue.add(contribInPeriod);
            if (adjustedStart.signum() <= 0) {
                continue;
            }
            BigDecimal periodReturn = endValue.divide(adjustedStart, MC);
            growthFactor = growthFactor.multiply(periodReturn, MC);
        }
        return growthFactor.subtract(ONE, MC);
    }

    /**
     * Maximum drawdown on the contribution-normalized equity curve, as a
     * negative decimal (-0.20 = a 20% peak-to-trough decline; 0 = no decline).
     * Returns {@code null} when there are fewer than two usable snapshots.
     *
     * <p>The curve is the same geometric chain of sub-period returns used by
     * {@link #computeTwr}: a contribution is added to the START value of its
     * period, so a cash injection moves the index by the period <em>return</em>
     * only — never by the raw dollar step. Drawdown is the largest decline of
     * the running index below its prior peak.</p>
     *
     * <p>This removes the artifact that corrupts a naive peak/trough scan over
     * raw portfolio value: a $200 → $400 contribution reads as a +100% step,
     * and the pre-contribution value then looks like a ~50% drawdown against
     * the post-contribution peak. With no contributions the normalized curve is
     * the raw value curve scaled by a constant, so this matches a naive
     * raw-value drawdown exactly.</p>
     */
    public static BigDecimal computeMaxDrawdown(List<PortfolioSnapshot> snapshots,
                                                List<Contribution> contributions) {
        if (snapshots == null || snapshots.size() < 2) {
            return null;
        }
        List<PortfolioSnapshot> sorted = new ArrayList<>(snapshots);
        sorted.sort(Comparator.comparing(PortfolioSnapshot::getTimestamp));

        List<Contribution> sortedContribs = sortedContributions(contributions);

        BigDecimal firstValue = toBigDecimal(sorted.get(0).getTotalValue());
        if (firstValue == null || firstValue.signum() <= 0) {
            return null;
        }

        BigDecimal index = ONE;                 // normalized equity, starts at 1.0
        BigDecimal peak = ONE;
        BigDecimal maxDrawdown = BigDecimal.ZERO; // <= 0

        for (int i = 1; i < sorted.size(); i++) {
            PortfolioSnapshot prev = sorted.get(i - 1);
            PortfolioSnapshot curr = sorted.get(i);
            BigDecimal startValue = toBigDecimal(prev.getTotalValue());
            BigDecimal endValue = toBigDecimal(curr.getTotalValue());
            if (startValue == null || endValue == null || startValue.signum() <= 0) {
                continue;
            }

            BigDecimal contribInPeriod = contributionsInPeriod(sortedContribs,
                    prev.getTimestamp(), curr.getTimestamp());
            BigDecimal adjustedStart = startValue.add(contribInPeriod);
            if (adjustedStart.signum() <= 0) {
                continue;
            }
            BigDecimal periodReturn = endValue.divide(adjustedStart, MC);
            index = index.multiply(periodReturn, MC);
            if (index.compareTo(peak) > 0) {
                peak = index;
            }
            BigDecimal drawdown = index.subtract(peak, MC).divide(peak, MC); // <= 0
            if (drawdown.compareTo(maxDrawdown) < 0) {
                maxDrawdown = drawdown;
            }
        }
        return maxDrawdown;
    }

    /**
     * Money-weighted return (IRR) as an annualized decimal (0.10 = +10%/yr).
     * Returns {@code null} on insufficient data or non-convergence.
     *
     * <p>Cashflows: initial cash and every contribution are outflows
     * (negative); the terminal portfolio value is the inflow (positive).</p>
     *
     * <p>Also returns {@code null} when the horizon is shorter than
     * {@link #MIN_MWR_HORIZON_YEARS}. Callers must render that as "not available yet"
     * rather than as zero.</p>
     *
     * <p>{@code simulationStart} and {@code asOf} must bound the SIMULATED period, not the
     * wall-clock run. A backtest covering seven simulated months but finishing in fifteen
     * minutes would otherwise annualise over ~0.002 years.</p>
     *
     * @param initialCash      the simulation's seed cash, dated {@code simulationStart}
     * @param simulationStart  start instant of the first cashflow
     * @param contributions    cash injected after start; each at its own date
     * @param currentValue     the portfolio's current value (the redemption inflow)
     * @param asOf             the date the value is computed at
     */
    public static BigDecimal computeMwr(Money initialCash,
                                        Instant simulationStart,
                                        List<Contribution> contributions,
                                        Money currentValue,
                                        Instant asOf) {
        if (initialCash == null || simulationStart == null
                || currentValue == null || asOf == null) {
            return null;
        }
        BigDecimal initial = initialCash.amount();
        BigDecimal terminal = currentValue.amount();
        if (initial.signum() <= 0 || terminal.signum() <= 0) {
            return null;
        }
        if (totalBasis(initialCash, contributions).compareTo(MIN_BASIS_USD) < 0) {
            return null;
        }

        // Build cashflow series: outflows negative, terminal inflow positive.
        List<double[]> flows = new ArrayList<>();
        flows.add(new double[]{ -initial.doubleValue(), 0.0 });
        if (contributions != null) {
            for (Contribution c : contributions) {
                double years = yearsBetween(simulationStart, c.contributionDate());
                if (years < 0) continue; // contribution dated before sim start — ignore
                flows.add(new double[]{ -c.amount().amount().doubleValue(), years });
            }
        }
        double horizonYears = yearsBetween(simulationStart, asOf);
        if (horizonYears <= 0) {
            return null; // can't annualize before time has elapsed
        }
        if (horizonYears < MIN_MWR_HORIZON_YEARS) {
            // Annualising a horizon this short is arithmetically correct and practically
            // useless: a 3-day run divides by ~0.008 years, so an ordinary 2% move reports as
            // several hundred percent a year. Report nothing rather than a number that reads
            // as a performance claim.
            return null;
        }
        flows.add(new double[]{ terminal.doubleValue(), horizonYears });

        Double irr = newtonRaphsonIrr(flows, IRR_INITIAL_GUESS.doubleValue());
        if (irr == null || Double.isNaN(irr) || Double.isInfinite(irr)) {
            return null;
        }
        return BigDecimal.valueOf(irr).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * The instant a simulation's terminal value should be dated at when computing
     * {@link #computeMwr}.
     *
     * <p>Prefers the last snapshot, because that is where {@code currentValue} was actually
     * measured; falls back to the simulation's end date, and only then to now — which is correct
     * for a LIVE run that has not snapshotted yet, and is the only case where the wall clock
     * belongs in this calculation at all.</p>
     *
     * <p>This lives here rather than in the adapters because MWR is annualised: the as-of instant
     * changes the number. The REST mapper and the MCP tool each inlined their own copy of this
     * chain, and the REST caller then passed {@code Instant.now()} — so one finished backtest
     * reported 59.74% over REST (falling on every read) and 66.39% over MCP. Two surfaces must
     * not disagree about one run's return.</p>
     *
     * @param snapshots portfolio snapshots in chronological order (may be null/empty)
     * @param endDate   the simulation's end date (null for an ongoing LIVE run)
     * @return the as-of instant, never null
     */
    public static Instant resolveMwrAsOf(List<PortfolioSnapshot> snapshots, Instant endDate) {
        if (snapshots != null && !snapshots.isEmpty()) {
            Instant lastSnapshotAt = snapshots.get(snapshots.size() - 1).getTimestamp();
            if (lastSnapshotAt != null) {
                return lastSnapshotAt;
            }
        }
        return endDate != null ? endDate : Instant.now();
    }

    /** Convenience: render a decimal return (0.1234) as a percentage (12.34). */
    public static BigDecimal asPercent(BigDecimal decimalReturn) {
        if (decimalReturn == null) return null;
        return decimalReturn.multiply(ONE_HUNDRED, MC).setScale(4, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private static List<Contribution> sortedContributions(List<Contribution> contributions) {
        if (contributions == null || contributions.isEmpty()) {
            return List.of();
        }
        List<Contribution> sorted = new ArrayList<>(contributions);
        // Same instant the period attribution uses, so ordering and membership agree.
        sorted.sort(Comparator.comparing(PerformanceCalculator::effectiveInstant));
        return sorted;
    }

    /**
     * Sum of contributions in the half-open interval [periodStart, periodEnd).
     * Attribution to the start of period [periodStart, periodEnd) means a
     * contribution at exactly the boundary counts toward the START of this period
     * (and is excluded from the previous period that ENDS at the same instant).
     */
    private static BigDecimal contributionsInPeriod(List<Contribution> sorted,
                                                    Instant periodStart, Instant periodEnd) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Contribution c : sorted) {
            Instant t = effectiveInstant(c);
            if (!t.isBefore(periodStart) && t.isBefore(periodEnd)) {
                sum = sum.add(c.amount().amount());
            }
        }
        return sum;
    }

    /**
     * When the cash actually landed, for sub-period attribution.
     *
     * <p>{@code contributionDate} is truncated to the day so that
     * {@code (planId, contributionDate)} can key the idempotency index. That makes it useless for
     * attribution: a contribution recorded at 19:39 carries a timestamp of 00:00, which falls
     * before every intraday snapshot and is therefore attributed to no period at all. The injected
     * cash then never reaches {@code adjustedStart}, and the value step it caused is read as
     * growth — a $500 top-up into a $500 portfolio reported as +100% TWR on a sim that had not
     * traded. {@code createdAt} is the real instant, so prefer it and keep the day as a fallback
     * for rows written before it existed.</p>
     */
    private static Instant effectiveInstant(Contribution c) {
        return c.createdAt() != null ? c.createdAt() : c.contributionDate();
    }

    private static BigDecimal toBigDecimal(Money m) {
        return m == null ? null : m.amount();
    }

    private static BigDecimal totalBasis(Money initialCash, List<Contribution> contributions) {
        BigDecimal total = initialCash.amount();
        if (contributions != null) {
            for (Contribution c : contributions) {
                total = total.add(c.amount().amount());
            }
        }
        return total;
    }

    private static double yearsBetween(Instant from, Instant to) {
        // Seconds, not whole days. Truncating to days made every horizon shorter than 24 hours
        // evaluate to exactly 0, so computeMwr bailed with "can't annualize before time has
        // elapsed" and mwrPercent came back null for every young simulation — which, for
        // backtests that finish in minutes, meant effectively always.
        long seconds = ChronoUnit.SECONDS.between(from, to);
        return seconds / (365.25 * 24 * 60 * 60);
    }

    /**
     * Newton-Raphson on NPV(r) = Σ cashflow_i / (1 + r)^t_i = 0.
     * cashflow at t=0 is included unchanged; later cashflows are discounted.
     * Returns null if it fails to converge inside {@link #IRR_MAX_ITERATIONS}.
     */
    private static Double newtonRaphsonIrr(List<double[]> flows, double guess) {
        double r = guess;
        double tol = IRR_TOLERANCE.doubleValue();
        for (int iter = 0; iter < IRR_MAX_ITERATIONS; iter++) {
            double npv = 0.0;
            double dnpv = 0.0;
            for (double[] flow : flows) {
                double amount = flow[0];
                double t = flow[1];
                double factor = Math.pow(1.0 + r, t);
                if (Double.isInfinite(factor) || Double.isNaN(factor) || factor == 0.0) {
                    return null;
                }
                npv += amount / factor;
                dnpv += -t * amount / (factor * (1.0 + r));
            }
            if (Math.abs(npv) < tol) {
                return r;
            }
            if (dnpv == 0.0) {
                return null;
            }
            double rNext = r - npv / dnpv;
            // Guard against runaway divergence (rates below -100% are non-physical).
            if (rNext <= -0.999999) {
                rNext = -0.99;
            }
            if (Math.abs(rNext - r) < tol) {
                return rNext;
            }
            r = rNext;
        }
        return null;
    }
}
