package com.quorumtrading.adapter.mcp.resource;

import com.quorumtrading.adapter.mcp.tool.GuideTools;
import java.util.Optional;
import java.lang.reflect.Parameter;
import io.quarkiverse.mcp.server.ToolArg;
import com.quorumtrading.adapter.mcp.tool.StrategyTools;
import com.quorumtrading.adapter.mcp.tool.SimulationTools;
import com.quorumtrading.adapter.mcp.tool.PortfolioTools;
import com.quorumtrading.adapter.mcp.tool.MarketDataTools;
import com.quorumtrading.adapter.mcp.tool.LlmProviderTools;
import com.quorumtrading.adapter.mcp.tool.InvestmentPlanTools;
import com.quorumtrading.adapter.mcp.tool.HistoricalEventTools;
import com.quorumtrading.adapter.mcp.tool.ConsensusTools;
import com.quorumtrading.adapter.mcp.tool.ConfigTools;
import com.quorumtrading.adapter.mcp.tool.CleanupTools;
import com.quorumtrading.adapter.mcp.tool.ChallengeTools;
import com.quorumtrading.adapter.mcp.tool.BrokerAccountTools;
import com.quorumtrading.adapter.mcp.tool.BenchmarkTools;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.adapter.mcp.tool.NotificationTools;
import com.quorumtrading.application.service.TickerValidationHelper;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.valueobject.IndicatorValue;
import com.quorumtrading.model.valueobject.MIC;
import com.quorumtrading.model.valueobject.Symbol;
import io.quarkiverse.mcp.server.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards the guides against citing themselves incorrectly.
 *
 * <p>Every first-time-user audit of this product, across two rounds and six
 * independent runs, hit the same wall: {@code platform-overview} instructed the
 * reader to call {@code get_guide("news_availability")}, and that topic does not
 * exist. It is a trivial defect with an outsized cost — it is the first broken
 * thing a diligent new user meets, in the document whose entire job is to orient
 * them, and it teaches them within their first few calls that the documentation
 * cannot be trusted.</p>
 *
 * <p>Editing the one bad reference would fix today's instance and nothing else.
 * The guides are long strings that cross-reference each other by slug, so the
 * next rename recreates it silently. This test makes such a reference impossible
 * to merge.</p>
 */
class GuideContentCrossReferenceTest {

    /** Matches get_guide("slug") / get_guide('slug') as written inside guide prose. */
    private static final Pattern GUIDE_REFERENCE =
        Pattern.compile("get_guide\\s*\\(\\s*[\"']([a-zA-Z0-9_-]+)[\"']\\s*\\)");

    @Test
    @DisplayName("every guide topic cited inside a guide actually resolves")
    void everyCitedTopicExists() {
        List<String> broken = new ArrayList<>();

        for (Map.Entry<String, String> guide : GuideContent.GUIDES.entrySet()) {
            Matcher matcher = GUIDE_REFERENCE.matcher(guide.getValue());
            while (matcher.find()) {
                String citedTopic = matcher.group(1);
                if (!GuideContent.GUIDES.containsKey(citedTopic)) {
                    broken.add(String.format(
                        "guide '%s' tells the reader to call get_guide(\"%s\"), which is not a topic",
                        guide.getKey(), citedTopic));
                }
            }
        }

        assertThat(broken)
            .as("a guide that sends the reader to a topic that errors is worse than one that "
                + "says nothing — valid topics are: %s", GuideContent.GUIDES.keySet())
            .isEmpty();
    }

    @Test
    @DisplayName("every topic has a description, and every description a topic")
    void topicsAndDescriptionsAgree() {
        // The listing is built from DESCRIPTIONS; a topic missing one is invisible in
        // the index even though get_guide would serve it, and a description with no
        // topic advertises something that errors on the first call.
        assertThat(GuideContent.DESCRIPTIONS.keySet())
            .as("every guide must be discoverable from the index")
            .containsExactlyInAnyOrderElementsOf(GuideContent.GUIDES.keySet());
    }

    @Test
    @DisplayName("no guide is empty or a placeholder")
    void guidesHaveContent() {
        GuideContent.GUIDES.forEach((topic, content) -> assertThat(content)
            .as("guide '%s' must carry real content", topic)
            .isNotNull()
            .isNotBlank());
    }

    /** The moving-average periods the platform actually computes, read from the domain type. */
    private static final Set<Integer> COMPUTED_MA_PERIODS = computedMovingAveragePeriods();

    private static Set<Integer> computedMovingAveragePeriods() {
        Pattern trailingDigits = Pattern.compile("(\\d+)$");
        Set<Integer> periods = new TreeSet<>();
        for (RecordComponent component : IndicatorValue.MovingAverageValue.class.getRecordComponents()) {
            Matcher m = trailingDigits.matcher(component.getName()); // sma50, ema200, ...
            if (m.find()) {
                periods.add(Integer.valueOf(m.group(1)));
            }
        }
        return periods;
    }

    /**
     * A period actually bound to a moving average — {@code SMA-20}, {@code EMA 50},
     * {@code 20-day SMA}, {@code 20-period EMA} — and nothing else.
     *
     * <p>The first version scanned every integer on any line mentioning SMA or EMA, which is
     * brittle in both directions: it would fail on "SMA-50 above SMA-200 for 5 days", and once
     * pointed at the agent prompts it flagged "RSI below 30", "+3%" and "Rule 1" as moving-average
     * periods. A guard that cries wolf gets deleted, which is the worst outcome of all.</p>
     */
    private static final Pattern MOVING_AVERAGE_PERIOD = Pattern.compile(
        "(?:SMA|EMA)[\\s-]?(\\d{1,3})|(\\d{1,3})[\\s-]?(?:day|period)[\\s-]?(?:SMA|EMA)",
        Pattern.CASE_INSENSITIVE);

    /** The agent prompt files, which describe the same indicator set the guides do. */
    private static final List<String> PROMPT_RESOURCES = List.of(
        "prompts/strategy-designer.md",
        "prompts/viability-assessor.md",
        "prompts/news-sentiment-analyst.md",
        "prompts/researcher-coordinator.md",
        "prompts/strategy-picker.md",
        "prompts/template-selector.md");

    /**
     * Every document that describes the platform to a reader or to an agent, keyed by name.
     *
     * <p>CLAUDE.md declares the guides, the agent prompts and the chat tool registry a single
     * keep-in-sync set. The guards covered only the guides — one third of it — and the SMA-20
     * defect they were written for was sitting in the prompts the entire time, in
     * {@code strategy-designer.md}, which is the file that <b>generates</b> strategies. A
     * fictional indicator there does not merely mislead a reader; it manufactures strategies
     * whose rules can never be evaluated.</p>
     */
    private static Map<String, String> documentationCorpus() {
        Map<String, String> corpus = new java.util.LinkedHashMap<>(GuideContent.GUIDES);
        for (String resource : PROMPT_RESOURCES) {
            corpus.put(resource, readClasspathResource(resource));
        }
        return corpus;
    }

    private static String readClasspathResource(String resource) {
        try (java.io.InputStream in = GuideContentCrossReferenceTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertThat(in).as("prompt resource %s not on the test classpath", resource).isNotNull();
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError("Could not read " + resource, e);
        }
    }

    @Test
    @DisplayName("no guide advertises a moving average the platform does not compute")
    void guidesOnlyCiteMovingAveragePeriodsThatExist() {
        // The strategy cheatsheet advertised "SMA — 20/50/200 period". There is no SMA-20:
        // MovingAverageValue carries sma50/sma200/ema50/ema200 and nothing else. A new user
        // followed the cheatsheet literally, made "price above the 20-day SMA" their primary
        // BUY rule, and the technical agent quietly improvised a substitute every round — so
        // the rule was never evaluated, and the run still reported trades and a return.
        //
        // Periods are read from the domain type rather than hardcoded, so adding a real
        // SMA-20 one day makes the guides legal automatically instead of failing this test.
        List<String> offenders = new ArrayList<>();

        for (Map.Entry<String, String> guide : documentationCorpus().entrySet()) {
            String section = "";
            for (String line : guide.getValue().split("\\R")) {
                if (line.strip().startsWith("#")) {
                    section = line;
                }
                if (!line.contains("SMA") && !line.contains("EMA")) {
                    continue;
                }
                // Bollinger's middle band genuinely IS a 20-period SMA, and MACD is genuinely
                // built from 12/26/9 EMAs — those are a different indicator's internals, not a
                // moving average on offer. The naming can be on the line or on the heading it
                // sits under ("### Bollinger Bands" / "Three bands around a 20-period SMA").
                String context = line + " " + section;
                if (context.contains("Bollinger") || context.contains("MACD")) {
                    continue;
                }
                Matcher numbers = MOVING_AVERAGE_PERIOD.matcher(line);
                while (numbers.find()) {
                    String captured = numbers.group(1) != null ? numbers.group(1) : numbers.group(2);
                    Integer period = Integer.valueOf(captured);
                    if (!COMPUTED_MA_PERIODS.contains(period)) {
                        offenders.add(String.format(
                            "guide '%s' cites a %d-period moving average: %s",
                            guide.getKey(), period, line.trim()));
                    }
                }
            }
        }

        assertThat(offenders)
            .as("an indicator named in a guide but never computed is worse than one left out — "
                + "the agent improvises a substitute instead of reporting the rule as unevaluable. "
                + "Computed periods are: %s", COMPUTED_MA_PERIODS)
            .isEmpty();
    }

    // =====================================================================
    // Drift guards.
    //
    // Four separate defects in one round shared a single shape: a fix changed behaviour and
    // the surrounding text or the sibling surface was not swept with it. Fixing the four
    // instances leaves the fifth to happen. These assertions fail the build on the shape.
    // =====================================================================

    @Test
    @DisplayName("the get_guide tool description lists every registered topic")
    void toolDescriptionListsEveryTopic() {
        // A market-data guide was added to GUIDES and DESCRIPTIONS but not to the @Tool
        // description, whose topic list is a hand-maintained duplicate. An MCP client that
        // reads only the tool schema — which is most of them — never learned the topic
        // existed. All four audit agents reported it.
        //
        // The annotation needs a compile-time constant so it cannot be generated from the map;
        // this assertion is what keeps the duplicate honest.
        Tool tool = toolAnnotationOf("getGuide");
        String description = tool.description();

        assertThat(GuideContent.DESCRIPTIONS.keySet())
            .allSatisfy(topic -> assertThat(description)
                .as("get_guide's description must name topic '%s' — a topic missing from it is "
                    + "invisible to any client that reads the schema rather than calling with "
                    + "no arguments", topic)
                .contains(topic));
    }

    @Test
    @DisplayName("no guide states a fixed default backtest evaluation time")
    void guidesDoNotClaimAHardCodedBacktestDefault() {
        // The default backtest schedule used to pin WEDNESDAY 10:00 regardless of timezone.
        // It now inherits the live schedule's own evaluation time, so ANY fixed clock time
        // presented as "the default" is wrong by construction — and three guide passages went
        // on asserting "Wednesday 10:00" after the behaviour changed, which is how three of
        // four markets withheld a "fixed" verdict on a fix that worked.
        List<String> offenders = new ArrayList<>();
        Pattern claim = Pattern.compile(
            "default[^.\\n]{0,40}(Wednesday|WEDNESDAY)[^.\\n]{0,20}\\d{1,2}:\\d{2}"
                + "|(Wednesday|WEDNESDAY)\\s+\\d{1,2}:\\d{2}[^.\\n]{0,20}default",
            Pattern.CASE_INSENSITIVE);

        for (Map.Entry<String, String> guide : GuideContent.GUIDES.entrySet()) {
            for (String line : guide.getValue().split("\\R")) {
                // The historical note deliberately quotes the old wording to correct it.
                if (line.contains("Older versions") || line.contains("out of date")) {
                    continue;
                }
                if (claim.matcher(line).find()) {
                    offenders.add(String.format("guide '%s': %s", guide.getKey(), line.trim()));
                }
            }
        }

        assertThat(offenders)
            .as("the omitted backtest_schedule inherits the live evaluation TIME — a guide "
                + "quoting a fixed time as the default mispredicts behaviour for every user "
                + "outside US Eastern")
            .isEmpty();
    }

    /** Matches the "Do NOT write" column of the market-data guide's ticker table. */
    private static final Pattern DO_NOT_WRITE_ROW =
        Pattern.compile("^\\s*\\|\\s*Crypto\\s*\\|(.*?)\\|(.*?)\\|\\s*$");

    @Test
    @DisplayName("every crypto ticker form the guides call invalid is actually rejected")
    void guidesDoNotPromiseRejectionsTheCodeDoesNotMake() {
        // market-data lists BTC-USD, BTC/USD and BTCUSD in a "Do NOT write" column. Two were
        // rejected and BTCUSD was accepted — so a brand-new document asserted a behaviour a
        // four-round-old defect contradicted. A silent hole is bad; a hole the docs promise is
        // closed is worse, because the user stops checking.
        List<String> promised = new ArrayList<>();
        for (String line : GuideContent.GUIDES.get("market-data").split("\\R")) {
            Matcher row = DO_NOT_WRITE_ROW.matcher(line);
            if (row.find()) {
                for (String token : row.group(2).split(",")) {
                    String cleaned = token.replace("`", "").trim();
                    if (!cleaned.isEmpty()) {
                        promised.add(cleaned);
                    }
                }
            }
        }

        assertThat(promised)
            .as("the market-data guide's crypto 'Do NOT write' column must be parseable, or "
                + "this guard silently asserts nothing")
            .isNotEmpty();

        List<String> accepted = new ArrayList<>();
        for (String form : promised) {
            if (isAcceptedAsCryptoTicker(form)) {
                accepted.add(form);
            }
        }

        assertThat(accepted)
            .as("these forms are printed as invalid in the market-data guide but the platform "
                + "accepts them, which creates a strategy that can never price or trade")
            .isEmpty();
    }

    /**
     * Real-world MICs to test the guide's US-venue claim against. Bounded on purpose: the claim
     * is checked in both directions over this universe, so adding a venue to {@link MIC#isUS()}
     * without documenting it fails here rather than silently contradicting the guide.
     */
    private static final List<String> CANDIDATE_MICS = List.of(
        "XNAS", "XNYS", "XASE", "ARCX", "BATS", "IEXG", "XCBO", "OOTC",
        "XETR", "XLON", "XPAR", "XTKS", "XAMS", "XSWX", "XMIL", "XMAD",
        "XHKG", "XTSE", "XASX", "XSTO", "XBRU", "XLIS", "XOSL", "XCSE");

    /** The backticked 4-letter MICs in the guide's "recognised US venues are …" sentence. */
    private static Set<String> usVenuesTheGuideClaims() {
        String guide = GuideContent.GUIDES.get("strategy-format");
        int start = guide.indexOf("recognised US venues are");
        if (start < 0) {
            return Set.of();
        }
        String sentence = guide.substring(start, Math.min(start + 220, guide.length()));
        Set<String> claimed = new TreeSet<>();
        Matcher m = Pattern.compile("`([A-Z]{4})`").matcher(sentence);
        while (m.find()) {
            claimed.add(m.group(1));
        }
        return claimed;
    }

    @Test
    @DisplayName("the guides' venue-suffix rule matches the one the code enforces")
    void guidesDescribeTheVenueSuffixRuleTheCodeActuallyApplies() {
        // Two guides said "exchange-qualified tickers are rejected" as a blanket claim while
        // AAPL:XNAS was accepted and silently normalised to AAPL. The rule is really "non-US
        // venues are rejected". A doc that over-promises a rejection teaches the user to stop
        // trusting it — and this one had already been reported once and never verified.
        Set<String> claimed = usVenuesTheGuideClaims();

        assertThat(claimed)
            .as("the strategy-format guide must name the recognised US venues, or this guard "
                + "silently asserts nothing")
            .isNotEmpty();

        Set<String> actuallyUs = new TreeSet<>();
        for (String code : CANDIDATE_MICS) {
            if (new MIC(code).isUS()) {
                actuallyUs.add(code);
            }
        }

        assertThat(claimed)
            .as("the US venues the strategy-format guide lists must be exactly those MIC.isUS() "
                + "accepts — a venue added to the code without the guide, or listed in the guide "
                + "without the code, is drift")
            .isEqualTo(actuallyUs);
    }

    @Test
    @DisplayName("a US venue suffix normalises to the bare ticker, a non-US one is refused")
    void venueSuffixBehaviourIsWhatTheGuidesNowPromise() {
        // The acceptance half of the claim. Symbol.parse must strip a US MIC to the bare ticker,
        // because price bars, position_limits keys and symbol=? queries are all keyed on it.
        assertThat(Symbol.parse("AAPL:XNAS").ticker())
            .as("a US venue suffix must normalise to the bare ticker the guide tells users to write")
            .isEqualTo("AAPL");

        StrategyConfig usVenue = new StrategyConfig();
        usVenue.setStockTickers(Set.of(Symbol.parse("AAPL:XNAS")));
        assertThatCode(() -> new TickerValidationHelper(null).validateTickers(usVenue))
            .as("the guide now says a US venue suffix is accepted")
            .doesNotThrowAnyException();

        // The rejection half. A non-US venue must still be refused, which is the part of the
        // original claim that was true and must not be lost while correcting the rest.
        assertThat(new MIC("XETR").isUS())
            .as("XETR is the guide's own example of a refused venue")
            .isFalse();
    }

    /**
     * Enumerated claims made in tool descriptions, mapped to the enum that decides the truth.
     *
     * <p>Add a row when a description starts listing the values of an enum. The label is the
     * literal prefix in the prose, so the guard reads the same list a caller reads.
     */
    private static final Map<String, Class<? extends Enum<?>>> ENUMERATED_CLAIMS = Map.of(
        "Types:", NotificationType.class,
        "Valid types:", NotificationType.class,
        "Valid channels:", NotificationChannel.class);

    /** Tool classes whose descriptions enumerate enum values. */
    private static final List<Class<?>> TOOL_CLASSES_WITH_ENUMERATED_CLAIMS =
        List.of(NotificationTools.class);

    @Test
    @DisplayName("a tool description that lists an enum's values lists all of them")
    void toolDescriptionsDoNotUnderReportEnumValues() {
        // update_notification_preference said "Valid types: …" and named 5 of NotificationType's
        // 10. The error message names all 10, because McpToolBase derives it from the enum while
        // the description is copy-pasted — which is exactly how the two drifted apart. The five
        // undocumented ones included BROKER_REAUTH_REQUIRED, the notification that tells a user
        // their live broker connection has died, so it was undiscoverable from the tool surface.
        List<String> problems = new ArrayList<>();
        int claimsChecked = 0;

        for (Class<?> toolClass : TOOL_CLASSES_WITH_ENUMERATED_CLAIMS) {
            for (Method method : toolClass.getDeclaredMethods()) {
                Tool tool = method.getAnnotation(Tool.class);
                if (tool == null) {
                    continue;
                }
                for (Map.Entry<String, Class<? extends Enum<?>>> claim : ENUMERATED_CLAIMS.entrySet()) {
                    int at = tool.description().indexOf(claim.getKey());
                    if (at < 0) {
                        continue;
                    }
                    claimsChecked++;
                    String listed = tool.description().substring(at + claim.getKey().length());
                    int stop = listed.indexOf('.');
                    if (stop >= 0) {
                        listed = listed.substring(0, stop);
                    }
                    Set<String> named = new TreeSet<>();
                    for (String token : listed.split(",")) {
                        String cleaned = token.trim();
                        if (!cleaned.isEmpty()) {
                            named.add(cleaned);
                        }
                    }
                    Set<String> actual = new TreeSet<>();
                    for (Enum<?> constant : claim.getValue().getEnumConstants()) {
                        actual.add(constant.name());
                    }
                    if (!named.equals(actual)) {
                        problems.add(String.format(
                            "%s says \"%s %s\" but %s has %s",
                            tool.name(), claim.getKey(), named, claim.getValue().getSimpleName(), actual));
                    }
                }
            }
        }

        assertThat(claimsChecked)
            .as("no enumerated claim was found to check — the prose labels in ENUMERATED_CLAIMS "
                + "have drifted from the descriptions, so this guard asserts nothing")
            .isPositive();

        assertThat(problems)
            .as("a description that lists an enum's values must list every one: a value the user "
                + "cannot discover is a feature they do not have")
            .isEmpty();
    }

    /** Tool classes to scan for arguments whose description and declaration disagree. */
    private static final List<Class<?>> ALL_TOOL_CLASSES = List.of(
        BenchmarkTools.class, BrokerAccountTools.class, ChallengeTools.class, CleanupTools.class,
        ConfigTools.class, ConsensusTools.class, GuideTools.class, HistoricalEventTools.class,
        InvestmentPlanTools.class, LlmProviderTools.class, MarketDataTools.class,
        NotificationTools.class, PortfolioTools.class, SimulationTools.class, StrategyTools.class);

    /** A description that promises the argument may be left out. */
    private static final Pattern OPTIONAL_SOUNDING = Pattern.compile(
        "^\\s*(optional|filter by|omit to|leave (blank|empty))", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("an argument described as optional is declared optional")
    void argumentsDescribedAsOptionalAreNotPublishedAsRequired() {
        // list_historical_events declared its three filters as bare String, which the MCP
        // framework publishes as REQUIRED and enforces before the method body runs. Calling it
        // with no arguments returned "Missing required argument: category", so listing the events
        // was impossible without naming a category, an asset class AND a sentiment — and the
        // method's own blank-handling was unreachable dead code. The unfiltered call is the whole
        // point of a list tool.
        List<String> mismatches = new ArrayList<>();
        int argumentsChecked = 0;

        for (Class<?> toolClass : ALL_TOOL_CLASSES) {
            for (Method method : toolClass.getDeclaredMethods()) {
                if (method.getAnnotation(Tool.class) == null) {
                    continue;
                }
                for (Parameter parameter : method.getParameters()) {
                    ToolArg arg = parameter.getAnnotation(ToolArg.class);
                    if (arg == null || !OPTIONAL_SOUNDING.matcher(arg.description()).find()) {
                        continue;
                    }
                    argumentsChecked++;
                    boolean declaredOptional =
                        Optional.class.equals(parameter.getType()) || !arg.required();
                    if (!declaredOptional) {
                        mismatches.add(String.format("%s(%s: %s) — \"%s…\"",
                            method.getName(), parameter.getName(),
                            parameter.getType().getSimpleName(),
                            arg.description().substring(0, Math.min(48, arg.description().length()))));
                    }
                }
            }
        }

        assertThat(argumentsChecked)
            .as("no optional-sounding argument was found — OPTIONAL_SOUNDING has drifted from the "
                + "descriptions, so this guard asserts nothing")
            .isPositive();

        assertThat(mismatches)
            .as("these arguments tell the caller they can be omitted, but are published as "
                + "required and rejected before the method runs")
            .isEmpty();
    }

    /** Whether a ticker form survives both the Symbol format check and the crypto pair check. */
    private static boolean isAcceptedAsCryptoTicker(String form) {
        StrategyConfig config = new StrategyConfig();
        try {
            config.setCryptoTickers(Set.of(new Symbol(form, InvestmentType.CRYPTO)));
        } catch (IllegalArgumentException rejectedBySymbolFormat) {
            return false;
        }
        try {
            // A null BrokerPort is safe: crypto never reaches the broker lookup, and the pair
            // check runs before it.
            new TickerValidationHelper(null).validateTickers(config);
            return true;
        } catch (IllegalArgumentException rejectedByPairCheck) {
            return false;
        }
    }

    /**
     * The tools a user needs in order to understand a finished run.
     *
     * <p>Not every tool needs documenting — this is the specific set whose absence was the one
     * unanimous weakness across four audit rounds: all four markets answered "could you work out
     * what your run did, from the docs alone?" as worse than first-try, and a grep found seven of
     * these eight cited in no guide at all.</p>
     */
    private static final List<String> RESULT_READING_TOOLS = List.of(
        "get_simulation",
        "query_consensus_decisions",
        "get_consensus_with_agents",
        "query_portfolio_snapshots",
        "get_simulation_cost",
        "estimate_backtest_cost",
        "get_strategy_performance",
        "noDecisionsReason");

    @Test
    @DisplayName("every tool needed to read a finished run is cited by at least one guide")
    void resultReadingToolsAreDocumentedSomewhere() {
        // Every other guard here checks whether a written claim is FALSE. None checked whether a
        // true thing was MISSING — so a corpus that documented none of these passed them all.
        // This is the omission-shaped counterpart, and it is the reason to keep it: re-closing
        // the gap without a guard leaves it free to re-open on the next edit.
        String corpus = String.join("\n", GuideContent.GUIDES.values());

        List<String> undocumented = RESULT_READING_TOOLS.stream()
            .filter(tool -> !corpus.contains(tool))
            .toList();

        assertThat(undocumented)
            .as("a tool a user cannot discover is a tool they do not have — cite each of these in "
                + "at least one guide (platform-overview's 'Reading Your Results' is the home for "
                + "them)")
            .isEmpty();
    }

    /**
     * A backticked {@code camelCase} token in guide prose is a claim about a field on a response
     * object. Frontmatter keys and tool names in this product are {@code snake_case} and asset
     * symbols are upper-case, so this shape isolates field claims specifically.
     */
    private static final Pattern BACKTICKED_CAMEL_CASE =
        Pattern.compile("`([a-z][a-zA-Z0-9]*[A-Z][a-zA-Z0-9]*)`");

    /**
     * camelCase words that are legitimately not DTO components — domain vocabulary and YAML values
     * rather than fields a caller reads off a response.
     */
    private static final Set<String> NOT_FIELD_CLAIMS = Set.of(
        "backtestSchedule", "liveSchedule", "marketOpen", "marketClose", "tradingDays",
        "evaluationTimes", "consensusThreshold", "positionLimits", "stockTickers", "cryptoTickers");

    @Test
    @DisplayName("every field a guide tells the user to read exists on an MCP DTO")
    void guidesOnlyCiteFieldsThatReachTheWire() {
        // THE blocker this exists for: `emptyRuns` was computed in the domain, validated in the
        // value object, unit-tested, and cited in platform-overview's "Reading Your Results" —
        // then dropped at BOTH adapter boundaries. A missing JSON key reads as "zero empty runs",
        // so the guide converted a diligent reader into a confidently wrong one.
        //
        // That is the shape six of eight fixes failed in one round: landed in the domain, stopped
        // at an adapter. Every other guard here asks "is this prose false?"; this one asks "does
        // the thing this prose promises actually reach the caller?"
        // Resolvable = anything a caller legitimately touches: a field they read off a response,
        // or an argument they pass in. `markdownInstructions` is the second kind, and treating
        // only the first kind as valid made this guard's own first run a false positive.
        Set<String> callerVisible = new TreeSet<>(mcpDtoComponentNames());
        callerVisible.addAll(toolParameterNames());
        // Not every response is a record. Several tools assemble their JSON as a Map, and a guide
        // may legitimately point at a REST field (e.g. the instance flags at /api/v1/instance).
        // Counting only MCP DTO components under-approximates "reaches the wire", which would turn
        // a true sentence into a build failure and teach people to widen NOT_FIELD_CLAIMS instead.
        callerVisible.addAll(toolResultMapKeys());
        callerVisible.addAll(restResponseComponentNames());
        assertThat(callerVisible)
            .as("DTO/parameter scan found nothing — this guard would assert vacuously")
            .isNotEmpty();

        List<String> unreachable = new ArrayList<>();
        for (Map.Entry<String, String> guide : GuideContent.GUIDES.entrySet()) {
            Matcher m = BACKTICKED_CAMEL_CASE.matcher(guide.getValue());
            while (m.find()) {
                String field = m.group(1);
                if (!NOT_FIELD_CLAIMS.contains(field) && !callerVisible.contains(field)) {
                    unreachable.add(String.format("guide '%s' cites `%s`", guide.getKey(), field));
                }
            }
        }

        assertThat(unreachable)
            .as("a guide that names a field the caller never receives is worse than silence — the "
                + "absent key reads as a zero value. Either thread the field to its DTO or stop "
                + "documenting it. Known non-field vocabulary lives in NOT_FIELD_CLAIMS.")
            .isEmpty();
    }

    /**
     * Matches the parameter name declared after a {@code @ToolArg(description = "…")}.
     *
     * <p>Anchored on {@code ")} — the annotation's real terminator — rather than on a
     * paren-free body: several descriptions contain {@code get_guide('topic')}, whose inner
     * parenthesis silently truncated the first version of this pattern and made the guard
     * report a false positive on its own first run.</p>
     */
    private static final Pattern TOOL_ARG_PARAMETER = Pattern.compile(
        "\"\\)\\s+(?:final\\s+)?[\\w.]+(?:<[^>]*>)?\\s+([a-zA-Z_][a-zA-Z0-9_]*)");

    /**
     * Every {@code @ToolArg} parameter name — the arguments a caller passes in.
     *
     * <p>Read from source rather than reflection because the build does not pass
     * {@code -parameters}, so {@code Parameter.getName()} yields {@code arg0}. Deriving them
     * instead of listing them matters: a hand-maintained list is precisely the thing that
     * drifted and created the defect this guard exists for.</p>
     */
    private static Set<String> toolParameterNames() {
        Set<String> names = new TreeSet<>();
        java.io.File toolDir = new java.io.File(
            "src/main/java/com/quorumtrading/adapter/mcp/tool");
        assertThat(toolDir)
            .as("tool sources not found at %s — resolve the path before trusting this guard",
                toolDir.getAbsolutePath())
            .exists();

        for (java.io.File file : java.util.Objects.requireNonNull(toolDir.listFiles())) {
            if (!file.getName().endsWith(".java")) {
                continue;
            }
            try {
                Matcher m = TOOL_ARG_PARAMETER.matcher(
                    java.nio.file.Files.readString(file.toPath()));
                while (m.find()) {
                    names.add(m.group(1));
                }
            } catch (java.io.IOException e) {
                throw new AssertionError("Could not read " + file, e);
            }
        }
        return names;
    }

    /**
     * camelCase keys that tools put into a Map-shaped response, e.g. {@code result.put("decisionCount", …)}.
     * These reach the caller exactly like a record component does; only the assembly differs.
     */
    private static Set<String> toolResultMapKeys() {
        Set<String> names = new TreeSet<>();
        Pattern mapKey = Pattern.compile("\\.put\\(\\s*\"([a-z][A-Za-z0-9]*)\"");
        java.io.File toolDir = new java.io.File("src/main/java/com/quorumtrading/adapter/mcp/tool");
        for (java.io.File file : java.util.Objects.requireNonNull(toolDir.listFiles())) {
            if (!file.getName().endsWith(".java")) {
                continue;
            }
            try {
                Matcher m = mapKey.matcher(java.nio.file.Files.readString(file.toPath()));
                while (m.find()) {
                    names.add(m.group(1));
                }
            } catch (java.io.IOException e) {
                throw new AssertionError("Could not read " + file, e);
            }
        }
        return names;
    }

    /**
     * Record components of the REST response DTOs, read from source because that module is not on
     * this one's classpath. A guide may point a reader at a REST endpoint for something the MCP
     * surface does not expose — the instance feature flags, for one — and that is still a field the
     * caller receives.
     */
    private static Set<String> restResponseComponentNames() {
        Set<String> names = new TreeSet<>();
        java.io.File dir = new java.io.File(
            "../quorumtrading-adapter-rest/src/main/java/com/quorumtrading/adapter/rest/dto/response");
        if (!dir.isDirectory()) {
            return names;
        }
        Pattern component = Pattern.compile("\\b(?:boolean|int|long|double|String|BigDecimal|Instant|"
            + "Boolean|Integer|Long|Double|List<[^>]+>|Map<[^>]+>)\\s+([a-z][A-Za-z0-9]*)\\s*[,)]");
        for (java.io.File file : java.util.Objects.requireNonNull(dir.listFiles())) {
            if (!file.getName().endsWith(".java")) {
                continue;
            }
            try {
                Matcher m = component.matcher(java.nio.file.Files.readString(file.toPath()));
                while (m.find()) {
                    names.add(m.group(1));
                }
            } catch (java.io.IOException e) {
                throw new AssertionError("Could not read " + file, e);
            }
        }
        return names;
    }

    /** Every record component name across the MCP DTOs, read off the compiled classes. */
    private static Set<String> mcpDtoComponentNames() {
        Set<String> names = new TreeSet<>();
        for (Class<?> dto : classesIn("com.quorumtrading.adapter.mcp.dto")) {
            if (!dto.isRecord()) {
                continue;
            }
            for (RecordComponent component : dto.getRecordComponents()) {
                names.add(component.getName());
            }
        }
        return names;
    }

    /**
     * Loads every class in a package off the compiled output, so neither this guard nor the tool
     * guard depends on a hand-maintained list — a list is the very thing that drifted.
     */
    private static List<Class<?>> classesIn(String packageName) {
        String path = packageName.replace('.', '/');
        java.net.URL root = GuideContentCrossReferenceTest.class.getClassLoader().getResource(path);
        assertThat(root).as("package %s not found on the test classpath", packageName).isNotNull();

        List<Class<?>> loaded = new ArrayList<>();
        java.io.File dir = new java.io.File(java.net.URI.create(root.toString()));
        for (java.io.File file : java.util.Objects.requireNonNull(dir.listFiles())) {
            if (!file.getName().endsWith(".class") || file.getName().contains("$")) {
                continue;
            }
            String className = packageName + "."
                + file.getName().substring(0, file.getName().length() - ".class".length());
            try {
                loaded.add(Class.forName(className));
            } catch (ClassNotFoundException e) {
                throw new AssertionError("Could not load " + className, e);
            }
        }
        return loaded;
    }

    private static Tool toolAnnotationOf(String methodName) {
        for (Method method : GuideTools.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && method.isAnnotationPresent(Tool.class)) {
                return method.getAnnotation(Tool.class);
            }
        }
        throw new AssertionError("No @Tool-annotated method named " + methodName + " on GuideTools");
    }
}
