package com.quorumtrading.model.service;

import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.valueobject.ContributionId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceCalculatorTest {

    private static final SimulationId SIM = new SimulationId(java.util.UUID.randomUUID());

    private static PortfolioSnapshot snap(Instant when, double total, double cash) {
        return new PortfolioSnapshot(
                SIM, when, Map.of(),
                Money.usd(BigDecimal.valueOf(total)),
                Money.usd(BigDecimal.valueOf(cash)));
    }

    private static Contribution contrib(Instant when, double amount) {
        // createdAt is the instant the cash landed and is what sub-period attribution uses;
        // contributionDate is truncated to the day in production purely to key the idempotency
        // index, so it cannot locate a contribution within a day.
        return new Contribution(
                ContributionId.generate(), SIM,
                Money.usd(BigDecimal.valueOf(amount)),
                when, null, null, when);
    }

    /** A stored contribution as the DCA path writes it: day-truncated date, real createdAt. */
    private static Contribution storedContrib(Instant landedAt, double amount) {
        return new Contribution(
                ContributionId.generate(), SIM,
                Money.usd(BigDecimal.valueOf(amount)),
                landedAt.truncatedTo(ChronoUnit.DAYS), null, null, landedAt);
    }

    // ---------- contribution snapshot must not read as growth ----------

    /**
     * The DCA path records a contribution and immediately appends a snapshot carrying the new
     * cash, so the injected money is visible in reported value. That snapshot must not be read as
     * a gain: a $500 top-up into a $500 portfolio that never traded reported +100% TWR, because
     * the stored contributionDate is truncated to the day and so fell before every intraday
     * snapshot, never reaching the sub-period's start value.
     */
    @Test
    void aContributionSnapshotIsNotInvestmentGain() {
        Instant t0 = Instant.parse("2026-07-27T19:39:46Z");
        Instant landed = Instant.parse("2026-07-27T19:39:51Z");

        List<PortfolioSnapshot> snapshots = List.of(
                snap(t0, 500, 500),
                snap(landed.plusMillis(1), 1000, 1000));

        BigDecimal twr = PerformanceCalculator.computeTwr(
                snapshots, List.of(storedContrib(landed, 500)));

        assertNotNull(twr);
        assertEquals(0, twr.setScale(4, java.math.RoundingMode.HALF_UP)
                .compareTo(new BigDecimal("0.0000")),
                "a contribution is a cashflow, not a gain; got " + twr);
    }

    /** Real growth after a contribution still registers. */
    @Test
    void growthAfterAContributionIsStillCounted() {
        Instant t0 = Instant.parse("2026-07-27T10:00:00Z");
        Instant landed = Instant.parse("2026-07-27T11:00:00Z");

        List<PortfolioSnapshot> snapshots = List.of(
                snap(t0, 500, 500),
                snap(landed.plusMillis(1), 1000, 1000),
                snap(Instant.parse("2026-07-28T10:00:00Z"), 1100, 1100));

        BigDecimal twr = PerformanceCalculator.computeTwr(
                snapshots, List.of(storedContrib(landed, 500)));

        assertEquals(0, twr.setScale(4, java.math.RoundingMode.HALF_UP)
                .compareTo(new BigDecimal("0.1000")),
                "genuine post-contribution growth must still register; got " + twr);
    }

    // ---------- totalInvested ----------

    @Test
    void totalInvestedSumsInitialPlusContributions() {
        Money result = PerformanceCalculator.totalInvested(
                Money.usd(new BigDecimal("1000")),
                List.of(
                        contrib(Instant.now(), 200),
                        contrib(Instant.now(), 200)));
        assertEquals(0, result.amount().compareTo(new BigDecimal("1400")));
    }

    @Test
    void totalInvestedNullContributionsTreatedAsEmpty() {
        Money result = PerformanceCalculator.totalInvested(
                Money.usd(new BigDecimal("500")), null);
        assertEquals(0, result.amount().compareTo(new BigDecimal("500")));
    }

    // ---------- simpleReturnPercent ----------

    @Test
    void simpleReturnPercentWithoutContributions() {
        // 1000 → 1100 = +10.0000%
        BigDecimal pct = PerformanceCalculator.simpleReturnPercent(
                Money.usd(new BigDecimal("1000")),
                List.of(),
                Money.usd(new BigDecimal("1100")));
        assertNotNull(pct);
        assertEquals(0, pct.compareTo(new BigDecimal("10")));
    }

    @Test
    void simpleReturnPercentNullContributionsTreatedAsNone() {
        BigDecimal pct = PerformanceCalculator.simpleReturnPercent(
                Money.usd(new BigDecimal("1000")),
                null,
                Money.usd(new BigDecimal("1100")));
        assertNotNull(pct);
        assertEquals(0, pct.compareTo(new BigDecimal("10")));
    }

    @Test
    void simpleReturnPercentCountsContributionsAsInvestedCapitalNotProfit() {
        // Seed 1000, top-up 500 → basis 1500. Final 1650 = +10%, not +65%.
        BigDecimal pct = PerformanceCalculator.simpleReturnPercent(
                Money.usd(new BigDecimal("1000")),
                List.of(contrib(Instant.now(), 500)),
                Money.usd(new BigDecimal("1650")));
        assertNotNull(pct);
        assertEquals(0, pct.compareTo(new BigDecimal("10")));
    }

    @Test
    void simpleReturnPercentNegativeReturn() {
        // 1000 → 900 = -10%
        BigDecimal pct = PerformanceCalculator.simpleReturnPercent(
                Money.usd(new BigDecimal("1000")),
                List.of(),
                Money.usd(new BigDecimal("900")));
        assertNotNull(pct);
        assertEquals(0, pct.compareTo(new BigDecimal("-10")));
    }

    @Test
    void simpleReturnPercentZeroInvestedIsUndefined() {
        assertNull(PerformanceCalculator.simpleReturnPercent(
                Money.usd(BigDecimal.ZERO),
                List.of(),
                Money.usd(new BigDecimal("100"))));
    }

    @Test
    void simpleReturnPercentNullInputsAreUndefined() {
        assertNull(PerformanceCalculator.simpleReturnPercent(
                null, List.of(), Money.usd(new BigDecimal("100"))));
        assertNull(PerformanceCalculator.simpleReturnPercent(
                Money.usd(new BigDecimal("100")), List.of(), null));
    }

    @Test
    void simpleReturnPercentCarriesFourRealDecimals() {
        // 300 / 7000 = 0.0428571…% → scale 4 HALF_UP = 0.0429.
        // The historical adapter formula divided first (0.000428… → 0.0004) and multiplied after,
        // yielding 0.0400: four printed decimals, two of them meaningless.
        BigDecimal pct = PerformanceCalculator.simpleReturnPercent(
                Money.usd(new BigDecimal("7000")),
                List.of(),
                Money.usd(new BigDecimal("7003")));
        assertNotNull(pct);
        assertEquals(new BigDecimal("0.0429"), pct,
                "the ×100 must precede the division, or the percentage loses two decimals");
    }

    @Test
    void simpleReturnPercentAgreesWithTimeWeightedReturnWhenThereAreNoContributions() {
        // Observed: one payload reported returnPercent 2.9500 beside twrPercent 2.9474 for a run
        // with no contributions, where the two are the same quantity by definition. The gap was
        // rounding, not disagreement — but a caller cannot tell those apart.
        BigDecimal pct = PerformanceCalculator.simpleReturnPercent(
                Money.usd(new BigDecimal("10000")),
                List.of(),
                Money.usd(new BigDecimal("10294.74")));

        assertNotNull(pct);
        assertEquals(new BigDecimal("2.9474"), pct);
    }

    @Test
    void simpleReturnPercentIsSelfConsistentWithTheValuesItIsGiven() {
        // A benchmark entry shipped currentValue 9400.89 on 10000 alongside returnPercent -5.9400,
        // which its own two numbers contradict.
        BigDecimal pct = PerformanceCalculator.simpleReturnPercent(
                Money.usd(new BigDecimal("10000")),
                List.of(),
                Money.usd(new BigDecimal("9400.89")));

        assertNotNull(pct);
        assertEquals(new BigDecimal("-5.9911"), pct);
    }

    // ---------- TWR ----------

    @Test
    void twrReturnsNullWithFewerThanTwoSnapshots() {
        Instant t = Instant.now();
        assertNull(PerformanceCalculator.computeTwr(
                List.of(snap(t, 1000, 1000)), List.of()));
    }

    @Test
    void twrWithoutContributionsMatchesSimpleReturn() {
        Instant t0 = Instant.now();
        // 1000 → 1100 = +10%
        BigDecimal twr = PerformanceCalculator.computeTwr(
                List.of(snap(t0, 1000, 1000), snap(t0.plus(1, ChronoUnit.DAYS), 1100, 0)),
                List.of());
        assertEquals(0, twr.compareTo(new BigDecimal("0.1")),
                "Simple 10% growth should give TWR = 0.1, got " + twr);
    }

    @Test
    void twrIgnoresContributionTiming() {
        // Period 1: 1000 → 1100 (+10%)
        // Contribution: $500 added before period 2
        // Period 2: 1600 → 1760 (+10%)
        // TWR = (1 + 0.10) × (1 + 0.10) − 1 = 0.21
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant t1 = t0.plus(30, ChronoUnit.DAYS);
        Instant t2 = t0.plus(60, ChronoUnit.DAYS);
        Instant contribAt = t0.plus(30, ChronoUnit.DAYS);

        List<PortfolioSnapshot> snapshots = List.of(
                snap(t0, 1000, 1000),
                snap(t1, 1100, 1100),
                snap(t2, 1760, 0));
        List<Contribution> contributions = List.of(contrib(contribAt, 500));

        BigDecimal twr = PerformanceCalculator.computeTwr(snapshots, contributions);
        assertNotNull(twr);
        // Allow small rounding tolerance
        double expected = 0.21;
        double actual = twr.doubleValue();
        assertEquals(expected, actual, 0.001,
                "TWR should be 21% regardless of contribution, got " + twr);
    }

    @Test
    void twrNegativeReturnsHandled() {
        Instant t0 = Instant.now();
        BigDecimal twr = PerformanceCalculator.computeTwr(
                List.of(snap(t0, 1000, 1000), snap(t0.plus(1, ChronoUnit.DAYS), 900, 0)),
                List.of());
        // 900/1000 - 1 = -0.10
        assertEquals(0, twr.compareTo(new BigDecimal("-0.1")));
    }

    // ---------- MWR ----------

    @Test
    void mwrReturnsNullForZeroHorizon() {
        Instant t = Instant.now();
        BigDecimal mwr = PerformanceCalculator.computeMwr(
                Money.usd(new BigDecimal("1000")), t, List.of(),
                Money.usd(new BigDecimal("1100")), t);
        assertNull(mwr);
    }

    @Test
    void mwrFlatNoContributionsApproximatesSimpleAnnualReturn() {
        // 1 year, 1000 → 1100 = +10% annualized
        Instant t0 = Instant.now().minus(365, ChronoUnit.DAYS);
        Instant now = Instant.now();
        BigDecimal mwr = PerformanceCalculator.computeMwr(
                Money.usd(new BigDecimal("1000")), t0, List.of(),
                Money.usd(new BigDecimal("1100")), now);
        assertNotNull(mwr);
        assertEquals(0.10, mwr.doubleValue(), 0.005,
                "MWR for 1000→1100 in 1y should be ~10%, got " + mwr);
    }

    @Test
    void mwrWithContributionDiffersFromTwr() {
        // 1000 initial, +500 at month 6, final 1700 after 1 year.
        // Outflows: -1000 at t=0, -500 at t=0.5y, inflow +1700 at t=1y.
        // Solve: -1000 -500/(1+r)^0.5 + 1700/(1+r) = 0 → r ≈ 0.135 (~13.5%)
        Instant t0 = Instant.now().minus(365, ChronoUnit.DAYS);
        Instant contribAt = t0.plus(182, ChronoUnit.DAYS);
        Instant now = Instant.now();
        BigDecimal mwr = PerformanceCalculator.computeMwr(
                Money.usd(new BigDecimal("1000")), t0,
                List.of(contrib(contribAt, 500)),
                Money.usd(new BigDecimal("1700")), now);
        assertNotNull(mwr, "MWR should converge");
        assertTrue(mwr.doubleValue() > 0.10 && mwr.doubleValue() < 0.20,
                "MWR should be between 10% and 20% given the cashflows, got " + mwr);
    }

    @Test
    void mwrReturnsNullBelowMinimumHorizon() {
        // A 3-day backtest annualises over ~0.008 years. The arithmetic is correct but the
        // number is useless: a +2% move over three days reports as several hundred percent
        // a year. Suppress it rather than publishing a figure nobody can act on.
        Instant t0 = Instant.now().minus(3, ChronoUnit.DAYS);
        Instant now = Instant.now();
        BigDecimal mwr = PerformanceCalculator.computeMwr(
                Money.usd(new BigDecimal("1000")), t0, List.of(),
                Money.usd(new BigDecimal("1020")), now);
        assertNull(mwr, "MWR over a 3-day horizon should be suppressed, got " + mwr);
    }

    @Test
    void mwrIsReportedAtTheMinimumHorizon() {
        // 31 days clears the floor, so the value must still be produced.
        Instant t0 = Instant.now().minus(31, ChronoUnit.DAYS);
        Instant now = Instant.now();
        BigDecimal mwr = PerformanceCalculator.computeMwr(
                Money.usd(new BigDecimal("1000")), t0, List.of(),
                Money.usd(new BigDecimal("1020")), now);
        assertNotNull(mwr, "MWR over a 31-day horizon should be reported");
    }

    @Test
    void mwrZeroTerminalValueReturnsNull() {
        Instant t0 = Instant.now().minus(365, ChronoUnit.DAYS);
        Instant now = Instant.now();
        BigDecimal mwr = PerformanceCalculator.computeMwr(
                Money.usd(new BigDecimal("1000")), t0, List.of(),
                Money.usd(BigDecimal.ZERO), now);
        assertNull(mwr);
    }

    // ---------- max drawdown ----------

    @Test
    void maxDrawdownReturnsNullWithFewerThanTwoSnapshots() {
        Instant t = Instant.now();
        assertNull(PerformanceCalculator.computeMaxDrawdown(
                List.of(snap(t, 1000, 1000)), List.of()));
    }

    @Test
    void maxDrawdownZeroWhenMonotonicallyIncreasing() {
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        BigDecimal dd = PerformanceCalculator.computeMaxDrawdown(
                List.of(
                        snap(t0, 1000, 1000),
                        snap(t0.plus(1, ChronoUnit.DAYS), 1100, 0),
                        snap(t0.plus(2, ChronoUnit.DAYS), 1200, 0)),
                List.of());
        assertNotNull(dd);
        assertEquals(0, dd.compareTo(BigDecimal.ZERO), "No decline ⇒ drawdown 0, got " + dd);
    }

    @Test
    void maxDrawdownWithoutContributionsMatchesRawValueDrawdown() {
        // 1000 → 1200 (peak) → 900 (trough) → 1100
        // worst draw = (900 - 1200) / 1200 = -0.25
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        BigDecimal dd = PerformanceCalculator.computeMaxDrawdown(
                List.of(
                        snap(t0, 1000, 1000),
                        snap(t0.plus(1, ChronoUnit.DAYS), 1200, 0),
                        snap(t0.plus(2, ChronoUnit.DAYS), 900, 0),
                        snap(t0.plus(3, ChronoUnit.DAYS), 1100, 0)),
                List.of());
        assertNotNull(dd);
        assertEquals(-0.25, dd.doubleValue(), 1e-9,
                "Drawdown should be -25%, got " + dd);
    }

    @Test
    void maxDrawdownNormalizesContributionStep() {
        // Sim seeded at 200; a $200 contribution lands mid-stream; value rises to 402.
        // Raw peak/trough scan would read the 200→400 cash step as a +100% jump and
        // call the pre-contribution 200 a ~50% drawdown. The normalized curve shows
        // the strategy never actually declined ⇒ drawdown ~ 0.
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant t1 = t0.plus(10, ChronoUnit.DAYS);
        Instant t2 = t0.plus(20, ChronoUnit.DAYS);
        Instant t3 = t0.plus(30, ChronoUnit.DAYS);
        Instant contribAt = t0.plus(15, ChronoUnit.DAYS); // within (t1, t2]

        List<PortfolioSnapshot> snapshots = List.of(
                snap(t0, 200, 200),
                snap(t1, 200, 200),
                snap(t2, 400, 400),   // jump is the $200 contribution, not a gain
                snap(t3, 402, 402));
        List<Contribution> contributions = List.of(contrib(contribAt, 200));

        BigDecimal dd = PerformanceCalculator.computeMaxDrawdown(snapshots, contributions);
        assertNotNull(dd);
        assertEquals(0.0, dd.doubleValue(), 1e-6,
                "Contribution must not register as a drawdown, got " + dd);
    }

    @Test
    void maxDrawdownDetectsRealDeclineDespiteContribution() {
        // Period 2 has a genuine -20% loss after the contribution.
        // Seed 1000 → 1000 (flat) ; +1000 contribution ; 2000 → 1600 (-20%).
        // Normalized: period1 = 1.0, period2 = 1600/2000 = 0.8 ⇒ index 0.8 ⇒ dd -0.20.
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant t1 = t0.plus(10, ChronoUnit.DAYS);
        Instant t2 = t0.plus(20, ChronoUnit.DAYS);
        Instant contribAt = t0.plus(15, ChronoUnit.DAYS);

        List<PortfolioSnapshot> snapshots = List.of(
                snap(t0, 1000, 1000),
                snap(t1, 1000, 1000),
                snap(t2, 1600, 1600));
        List<Contribution> contributions = List.of(contrib(contribAt, 1000));

        BigDecimal dd = PerformanceCalculator.computeMaxDrawdown(snapshots, contributions);
        assertNotNull(dd);
        assertEquals(-0.20, dd.doubleValue(), 1e-6,
                "Real -20% loss after a contribution must still show, got " + dd);
    }

    // ---------- asPercent ----------

    @Test
    void asPercentMultipliesBy100() {
        assertEquals(0, PerformanceCalculator.asPercent(new BigDecimal("0.1234"))
                .compareTo(new BigDecimal("12.3400")));
    }

    @Test
    void asPercentHandlesNull() {
        assertNull(PerformanceCalculator.asPercent(null));
    }

    // ---------- as-of resolution for MWR ----------

    /**
     * MWR is annualised, so the instant the terminal value is dated at changes the number. That
     * choice used to be inlined separately in the REST mapper and in the MCP tool — the same
     * chain, written twice — and the two surfaces then reported different returns for one
     * finished run. Resolving it here is what makes them agree by construction.
     */
    @Test
    void asOfPrefersTheLastSnapshotBecauseThatIsWhereValueWasMeasured() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant lastSnapshotAt = Instant.parse("2026-03-01T00:00:00Z");
        Instant endDate = Instant.parse("2026-04-01T00:00:00Z");

        Instant asOf = PerformanceCalculator.resolveMwrAsOf(
                List.of(snap(start, 100_000, 0), snap(lastSnapshotAt, 120_000, 0)), endDate);

        assertEquals(lastSnapshotAt, asOf,
                "currentValue is read off the latest snapshot, so the horizon must end there");
    }

    @Test
    void asOfFallsBackToTheSimulationEndDateWhenThereAreNoSnapshots() {
        Instant endDate = Instant.parse("2026-04-01T00:00:00Z");

        assertEquals(endDate, PerformanceCalculator.resolveMwrAsOf(List.of(), endDate));
        assertEquals(endDate, PerformanceCalculator.resolveMwrAsOf(null, endDate));
    }

    @Test
    void asOfFallsBackToNowOnlyWhenNothingElseIsKnown() {
        Instant before = Instant.now();
        Instant asOf = PerformanceCalculator.resolveMwrAsOf(null, null);

        assertNotNull(asOf);
        assertFalse(asOf.isBefore(before.minusSeconds(1)),
                "with no snapshot and no end date, a running simulation is measured as of now");
    }

    /**
     * The regression this guards: a COMPLETED run's horizon must not grow as the clock runs.
     * Anchoring to wall-clock made a finished backtest's reported MWR decay on every refresh —
     * ~1.25 points a day — so the same run quoted a different return every time it was opened.
     */
    @Test
    void asOfDoesNotDriftWithWallClockOnceASnapshotExists() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant lastSnapshotAt = Instant.parse("2026-03-01T00:00:00Z");
        List<PortfolioSnapshot> snapshots =
                List.of(snap(start, 100_000, 0), snap(lastSnapshotAt, 120_000, 0));
        Money initial = Money.usd(BigDecimal.valueOf(100_000));
        Money terminal = Money.usd(BigDecimal.valueOf(120_000));

        BigDecimal anchored = PerformanceCalculator.computeMwr(
                initial, start, List.of(), terminal,
                PerformanceCalculator.resolveMwrAsOf(snapshots, null));
        BigDecimal wallClock = PerformanceCalculator.computeMwr(
                initial, start, List.of(), terminal, Instant.now());

        assertNotNull(anchored);
        assertNotNull(wallClock);
        assertNotEquals(0, anchored.compareTo(wallClock),
                "a wall-clock horizon annualises a finished run over the wrong period — "
                        + "if these ever match, the test window stopped being informative");
    }
}
