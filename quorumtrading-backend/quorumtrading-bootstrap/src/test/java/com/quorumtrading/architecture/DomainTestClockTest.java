package com.quorumtrading.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A domain test that reads the wall clock is a test that passes on most days.
 *
 * <p>The domain already takes the clock as a parameter — {@code InvestmentPlan.create(…, now)},
 * {@code isDueAt(now)}, {@code pause(now)}, {@code BacktestWindow.extendsIntoTheFuture(…, now, zone)}.
 * A test that hands those methods {@code Instant.now()} throws that design away and couples its
 * result to the day, hour and timezone the suite happens to run in. Twice now that has put main in
 * the red for reasons that had nothing to do with the change being tested:</p>
 *
 * <ul>
 *   <li>{@code InvestmentPlanTest.isDueAtRespectsStatusAndTime} created a plan due on the 1st and
 *       asserted it was not yet due — true on 30 days a month, false on the 1st.</li>
 *   <li>{@code ContributionTest.liveSim} truncated {@code Instant.now()} to UTC days to build a LIVE
 *       simulation, which around local midnight resolved to the previous local day and flipped the
 *       resulting status.</li>
 * </ul>
 *
 * <p>Neither was caught by review, because both read as obviously harmless. This test makes the
 * category visible instead: the clock is fine where it is already frozen below, and a new use has
 * to be argued for.</p>
 *
 * <p><strong>This is a ratchet.</strong> The counts are a ceiling, not a target — they are meant to
 * fall. When you replace a {@code now()} with a fixed instant, lower the number in the same commit
 * so the next person cannot spend the headroom you freed. Raising one needs a reason in the commit
 * message; if you are raising it to make a build pass, pin the instant in the test instead.</p>
 *
 * <p>Scope is the domain module only. The application and adapter layers have genuine reasons to
 * observe real time (schedulers, rate limiters, integration tests against live infrastructure);
 * the domain does not.</p>
 */
class DomainTestClockTest {

    private static final String DOMAIN_TEST_ROOT =
        "quorumtrading-backend/quorumtrading-model/src/test/java";

    /**
     * Reads of the real clock. {@code Clock.system*} is included because handing a domain method a
     * system clock is the same mistake wearing a seatbelt.
     */
    private static final Pattern WALL_CLOCK = Pattern.compile(
        "\\b(?:Instant|LocalDate|LocalDateTime|LocalTime|ZonedDateTime|OffsetDateTime"
            + "|Year|YearMonth|MonthDay)\\s*\\.\\s*now\\s*\\("
            + "|\\bSystem\\s*\\.\\s*currentTimeMillis\\s*\\("
            + "|\\bClock\\s*\\.\\s*system[A-Za-z]*\\s*\\(");

    /**
     * Files that read the clock today, with the number of reads each is allowed. Paths are exact
     * and relative to {@link #DOMAIN_TEST_ROOT} — never globs — so a new test file cannot inherit
     * an exemption by sitting in an already-listed package.
     */
    private static final Map<String, Integer> FROZEN = new LinkedHashMap<>();

    static {
        FROZEN.put("com/quorumtrading/model/entity/SimulationTest.java", 58);
        FROZEN.put("com/quorumtrading/model/entity/BenchmarkTest.java", 32);
        FROZEN.put("com/quorumtrading/model/service/PerformanceCalculatorTest.java", 25);
        FROZEN.put("com/quorumtrading/model/valueobject/IndicatorContextTest.java", 24);
        FROZEN.put("com/quorumtrading/model/valueobject/BollingerBandsTest.java", 14);
        FROZEN.put("com/quorumtrading/model/valueobject/MonthlySpendTest.java", 12);
        FROZEN.put("com/quorumtrading/model/marketdata/DataAvailabilityTest.java", 11);
        FROZEN.put("com/quorumtrading/model/entity/ContributionTest.java", 9);
        FROZEN.put("com/quorumtrading/model/valueobject/RSITest.java", 6);
        FROZEN.put("com/quorumtrading/model/valueobject/MACDTest.java", 6);
        FROZEN.put("com/quorumtrading/model/marketdata/BackfillJobTest.java", 6);
        // Was 6 until the isDueAt test stopped asking what day it is (#74).
        FROZEN.put("com/quorumtrading/model/entity/InvestmentPlanTest.java", 5);
        FROZEN.put("com/quorumtrading/model/entity/BrokerAccountTest.java", 6);
        FROZEN.put("com/quorumtrading/model/valueobject/IndicatorSnapshotTest.java", 5);
        FROZEN.put("com/quorumtrading/model/entity/FeatureFlagTest.java", 4);
        FROZEN.put("com/quorumtrading/model/valueobject/MovingAverageTest.java", 3);
        FROZEN.put("com/quorumtrading/model/entity/UserBudgetTest.java", 2);
        FROZEN.put("com/quorumtrading/model/entity/MarketResearchTest.java", 2);
        FROZEN.put("com/quorumtrading/model/valueobject/PriceBarTest.java", 1);
        FROZEN.put("com/quorumtrading/model/valueobject/LLMModelInfoTest.java", 1);
    }

    private Path repoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("CLAUDE.md"))) {
            path = path.getParent();
        }
        assertThat(path).as("repository root containing CLAUDE.md").isNotNull();
        return path;
    }

    /**
     * Comments are stripped before counting. A test that explains why it pins its instant will
     * usually name {@code Instant.now()} in the sentence doing the explaining, and a guard that
     * penalises that is a guard that discourages the comment.
     *
     * <p>This is text stripping, not parsing: a {@code //} inside a string literal truncates the
     * rest of that line. The failure mode is a missed count on one line, never a false alarm, and
     * a domain test with a URL literal sharing a line with a clock read is not a thing that
     * happens. If it ever does, the fix is to pin the instant anyway.</p>
     */
    private static String withoutComments(String source) {
        return source
            .replaceAll("(?s)/\\*.*?\\*/", "")
            .replaceAll("(?m)//.*$", "");
    }

    private static int countWallClockReads(String source) {
        Matcher matcher = WALL_CLOCK.matcher(withoutComments(source));
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    @Test
    @DisplayName("no domain test reads the wall clock beyond its frozen allowance")
    void domainTestsDoNotReachForTheWallClock() throws IOException {
        Path root = repoRoot().resolve(DOMAIN_TEST_ROOT);
        assertThat(root).as("domain test sources — has the module moved?").isDirectory();

        List<String> newUses = new ArrayList<>();
        List<String> grown = new ArrayList<>();

        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                int found = countWallClockReads(Files.readString(file, StandardCharsets.UTF_8));
                if (found == 0) continue;

                String relative = root.relativize(file).toString().replace('\\', '/');
                Integer allowed = FROZEN.get(relative);

                if (allowed == null) {
                    newUses.add("  " + relative + " (" + found + ")");
                } else if (found > allowed) {
                    grown.add("  " + relative + ": " + found + " reads, " + allowed + " allowed");
                }
            }
        }

        assertThat(newUses)
            .as("""
                These domain tests read the real clock and are not on the frozen list.

                The domain takes the instant as a parameter — pass a fixed one (Instant.parse) \
                instead of Instant.now(). A test built on "whatever day it is" asserts something \
                different on the 1st of the month, at midnight, and in another timezone.

                If the clock genuinely belongs here, add the file to FROZEN with a reason in the \
                commit message.
                """)
            .isEmpty();

        assertThat(grown)
            .as("""
                These domain tests grew new reads of the real clock.

                The frozen counts are a ceiling that is meant to fall, not headroom to spend. Pin \
                the new test to a fixed instant.
                """)
            .isEmpty();
    }

    /**
     * The list is only a ratchet if it is anchored to something real. A stale entry — a file that
     * has since been deleted or renamed — silently exempts nothing and hides that the list has
     * drifted from the tree.
     */
    @Test
    @DisplayName("every frozen entry still points at a file that exists")
    void frozenListHasNoDeadEntries() {
        Path root = repoRoot().resolve(DOMAIN_TEST_ROOT);

        List<String> missing = FROZEN.keySet().stream()
            .filter(relative -> !Files.exists(root.resolve(relative)))
            .toList();

        assertThat(missing)
            .as("frozen entries pointing at files that no longer exist — remove them")
            .isEmpty();
    }

    /** A guard that cannot see the thing it guards is worse than no guard. */
    @Test
    @DisplayName("the detector recognises the forms this rule is about")
    void detectorMatchesTheKnownForms() {
        assertThat(countWallClockReads("Instant now = Instant.now();")).isEqualTo(1);
        assertThat(countWallClockReads("LocalDate.now(ZoneOffset.UTC)")).isEqualTo(1);
        assertThat(countWallClockReads("YearMonth.now().minusMonths(1)")).isEqualTo(1);
        assertThat(countWallClockReads("System.currentTimeMillis()")).isEqualTo(1);
        assertThat(countWallClockReads("Clock.systemDefaultZone()")).isEqualTo(1);
        assertThat(countWallClockReads("Clock.systemUTC()")).isEqualTo(1);

        assertThat(countWallClockReads("Instant.parse(\"2026-07-30T00:00:00Z\")"))
            .as("a pinned instant is the whole point and must not register")
            .isZero();
        assertThat(countWallClockReads("plan.isDueAt(now)"))
            .as("passing an instant in is the pattern we want")
            .isZero();
    }
}
