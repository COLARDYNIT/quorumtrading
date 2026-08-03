package com.quorumtrading.model.orchestration;

import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.enums.ConsensusMethod;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.TradingMode;
import com.quorumtrading.model.exception.StrategyValidationException;
import com.quorumtrading.model.valueobject.MIC;
import com.quorumtrading.model.valueobject.MarketHours;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.Symbol;
import org.yaml.snakeyaml.Yaml;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Domain-layer parser for strategy configuration files.
 * Converts markdown strategy files with YAML frontmatter into StrategyConfig domain objects.
 *
 * <p><b>Responsibilities:</b></p>
 * <ul>
 *   <li>Parse YAML frontmatter (---...---) format</li>
 *   <li>Sanitize input for encoding corruption from database retrieval</li>
 *   <li>Validate consensus method, thresholds, and ticker symbols</li>
 *   <li>Extract strategy prompt from markdown body</li>
 * </ul>
 *
 * <p><b>DDD Classification:</b></p>
 * This is a domain service - pure business logic with no infrastructure dependencies.
 * It interprets and validates strategy configurations according to business rules.
 *
 * @see StrategyConfig
 * @see ConsensusMethod
 */
public class StrategyConfigParser {

    private static final Yaml yaml = new Yaml();

    /**
     * Dotted-suffix → MIC mapping for European venue-qualified tickers (e.g.
     * {@code RHM.DE} → Xetra). These are the common exchange suffixes used in
     * strategy frontmatter; the canonical {@code ROOT:MIC} form is preferred, but
     * the suffix form is accepted as an ergonomic alias. A bare ticker whose suffix
     * is NOT in this table (e.g. {@code BRK.B}) is left untouched as a US ticker.
     */
    private static final Map<String, MIC> MIC_BY_SUFFIX = Map.ofEntries(
        Map.entry("DE", new MIC("XETR")),   // Germany — Xetra
        Map.entry("PA", new MIC("XPAR")),   // France — Euronext Paris
        Map.entry("AS", new MIC("XAMS")),   // Netherlands — Euronext Amsterdam
        Map.entry("BR", new MIC("XBRU")),   // Belgium — Euronext Brussels
        Map.entry("MI", new MIC("XMIL")),   // Italy — Borsa Italiana / Euronext Milan
        Map.entry("MC", new MIC("XMAD")),   // Spain — Bolsa de Madrid
        Map.entry("LS", new MIC("XLIS")),   // Portugal — Euronext Lisbon
        Map.entry("L", new MIC("XLON")),    // UK — London Stock Exchange
        Map.entry("SW", new MIC("XSWX")),   // Switzerland — SIX Swiss Exchange
        Map.entry("VI", new MIC("XWBO")),   // Austria — Wiener Börse
        Map.entry("HE", new MIC("XHEL")),   // Finland — Nasdaq Helsinki
        Map.entry("ST", new MIC("XSTO")),   // Sweden — Nasdaq Stockholm
        Map.entry("CO", new MIC("XCSE")),   // Denmark — Nasdaq Copenhagen
        Map.entry("OL", new MIC("XOSL")),   // Norway — Oslo Børs
        Map.entry("IR", new MIC("XDUB"))    // Ireland — Euronext Dublin
    );

    /**
     * MIC → settlement-currency default for European venues. Used when a strategy
     * entry does not carry an explicit currency. Unlisted (e.g. US) MICs fall back
     * to USD in {@link #buildEuropeanSymbol}.
     */
    private static final Map<String, Money.Currency> CURRENCY_BY_MIC = Map.ofEntries(
        Map.entry("XETR", Money.Currency.EUR),
        Map.entry("XPAR", Money.Currency.EUR),
        Map.entry("XAMS", Money.Currency.EUR),
        Map.entry("XBRU", Money.Currency.EUR),
        Map.entry("XMIL", Money.Currency.EUR),
        Map.entry("XMAD", Money.Currency.EUR),
        Map.entry("XLIS", Money.Currency.EUR),
        Map.entry("XHEL", Money.Currency.EUR),
        Map.entry("XWBO", Money.Currency.EUR),
        Map.entry("XDUB", Money.Currency.EUR)
    );

    /**
     * Parses a strategy markdown file into a StrategyConfig domain object.
     * Requires valid YAML frontmatter format (---...---).
     * Fails fast if format is invalid - no fallback parsing.
     *
     * IMPORTANT: Sanitizes input to handle encoding corruption from database retrieval.
     * Removes BOM markers and normalizes line endings across platforms.
     *
     * @param markdown The markdown content with YAML frontmatter
     * @return StrategyConfig domain object
     * @throws IllegalArgumentException if markdown is null/empty or has invalid format
     */
    public StrategyConfig parse(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            throw new IllegalArgumentException("Markdown input cannot be null or empty");
        }

        // Sanitize input: Remove BOM and normalize for database encoding issues
        String sanitized = sanitizeInput(markdown);
        return parseYamlFrontmatter(sanitized);
    }

    /**
     * Sanitizes markdown input to handle encoding corruption and platform differences.
     * Removes BOM markers that can be introduced by database drivers on different systems.
     *
     * CRITICAL FIX for issue: "Index out of bounds for length -1" on other MacBooks
     * - UTF-8 BOM (U+FEFF) causes SnakeYAML parser to fail with array corruption
     * - Different PostgreSQL connection encodings on different systems introduce invisible chars
     *
     * @param markdown The raw markdown input
     * @return Sanitized markdown ready for parsing
     */
    private String sanitizeInput(String markdown) {
        String result = markdown;

        // Remove UTF-8 BOM if present (U+FEFF = \uFEFF)
        if (result.length() > 0 && result.charAt(0) == '\uFEFF') {
            result = result.substring(1);
        }

        // Normalize line endings to Unix format (handles cross-platform DB retrieval)
        result = result.replace("\r\n", "\n").replace("\r", "\n");

        // Remove other zero-width problematic characters that corrupt YAML parsing
        // Zero-width space (U+200B), zero-width joiner (U+200D), zero-width no-break space (U+FEFF when not at start)
        result = result.replaceAll("[\u200B\u200C\u200D\uFEFF]", "");

        return result;
    }

    /**
     * Parse YAML frontmatter format: ---\\nYAML\\n---\\nmarkdown body
     *
     * Supports two formats:
     * 1. Standard format: ---\nYAML\n---\nmarkdown (preferred)
     * 2. Legacy format: ---\nYAML\nmarkdown (missing closing ---, detected by markdown indicators)
     *
     * @param markdown The normalized markdown input
     * @return Parsed StrategyConfig
     * @throws IllegalArgumentException if frontmatter format is invalid
     */
    private StrategyConfig parseYamlFrontmatter(String markdown) {
        StrategyConfig config = new StrategyConfig();

        // Try standard frontmatter format first: ---\nYAML\n---\nmarkdown
        Pattern frontmatterPattern = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$", Pattern.DOTALL);
        Matcher matcher = frontmatterPattern.matcher(markdown);

        String frontmatterString;
        String markdownBody;

        if (matcher.find()) {
            frontmatterString = matcher.group(1).trim();
            markdownBody = matcher.group(2).trim();
        } else {
            // Try legacy format: ---\nYAML followed directly by markdown (missing closing ---)
            // Detect where YAML ends by looking for markdown indicators
            FrontmatterParseResult legacyResult = parseLegacyFrontmatter(markdown);
            if (legacyResult == null) {
                throw new IllegalArgumentException("Invalid YAML frontmatter format. Expected: ---\\n...yaml...\\n---\\n...markdown...");
            }
            frontmatterString = legacyResult.yaml;
            markdownBody = legacyResult.markdown;
        }

        // Parse YAML frontmatter with better error handling
        Map<String, Object> frontmatter;
        try {
            // Use a fresh Yaml instance per parse to avoid scanner state corruption
            // This prevents SnakeYAML scanner bugs from affecting subsequent parses
            Yaml localYaml = new Yaml();
            frontmatter = localYaml.load(frontmatterString);
            if (frontmatter == null) {
                frontmatter = new HashMap<>();
            }
        } catch (IndexOutOfBoundsException e) {
            // SnakeYAML scanner corruption - try alternative parsing approach
            // This can happen with complex YAML structures or corrupted input
            try {
                // Retry with StringReader wrapper
                Yaml localYaml = new Yaml();
                java.io.StringReader reader = new java.io.StringReader(frontmatterString);
                frontmatter = localYaml.load(reader);
                if (frontmatter == null) {
                    frontmatter = new HashMap<>();
                }
            } catch (Exception retryException) {
                throw new IllegalArgumentException("Failed to parse YAML frontmatter (recovery failed): " + retryException.getMessage() + "\nFrontmatter content:\n'" + frontmatterString +"'", retryException);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse YAML frontmatter: " + e.getMessage() + "\nFrontmatter content:\n'" + frontmatterString +"'", e);
        }

        // Extract config values from frontmatter with defaults
        config.setVersion(getStringOrDefault(frontmatter, "version", "v1.0"));

        config.setConsensusMethod(
            parseConsensusMethod(getStringOrDefault(frontmatter, "consensus_method", "ORCHESTRATOR_OVERRIDE"))
        );

        config.setConsensusThreshold(
            getDoubleOrDefault(frontmatter, "consensus_threshold", 0.6)
        );

        config.setReserveCashPercentage(
            getDoubleOrDefault(frontmatter, "reserve_cash_percentage", 0.2)
        );

        // Optional per-order ceiling: max fraction of portfolio value a single BUY may take.
        // Absent means "not configured" (null), which the execution engine treats differently
        // from an explicit value — see ExecuteConsensusDecisionService.
        if (frontmatter.containsKey("max_order_fraction")) {
            config.setMaxOrderFraction(convertToDouble(frontmatter.get("max_order_fraction")));
        }

        // Parse position_limits map if present
        if (frontmatter.containsKey("position_limits")) {
            Object posLimits = frontmatter.get("position_limits");
            if (posLimits instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rawLimits = (Map<String, Object>) posLimits;
                Map<String, Double> positionLimits = new HashMap<>();
                for (Map.Entry<String, Object> entry : rawLimits.entrySet()) {
                    positionLimits.put(entry.getKey(), convertToDouble(entry.getValue()));
                }
                config.setPositionLimits(positionLimits);
            } else {
                throw new IllegalArgumentException("Failed to parse YAML frontmatter: invalid position_limits\nFrontmatter content:\n" + frontmatterString);
            }
        }

        // Parse stock_tickers list (default to empty set)
        config.setStockTickers(parseTickerList(frontmatter, "stock_tickers", InvestmentType.STOCK));

        // Parse crypto_tickers list (no default - validation will enforce at least one ticker)
        Set<Symbol> cryptoTickers = parseTickerList(frontmatter, "crypto_tickers", InvestmentType.CRYPTO);
        config.setCryptoTickers(cryptoTickers);

        // Infer trading mode from presence of stock tickers
        boolean hasStockTickers = !config.getStockTickers().isEmpty();
        TradingMode tradingMode = hasStockTickers ? TradingMode.STOCK : TradingMode.CRYPTO;
        config.setTradingMode(tradingMode);

        // Parse dual schedule (live + backtest) with backward compatibility.
        // Runs before the unknown-key check so a legacy key gets its specific migration message
        // rather than a generic "unrecognised key".
        parseSchedules(frontmatter, config, tradingMode);

        rejectUnknownFrontmatterKeys(frontmatter);

        // Parse optional LLM model override
        if (frontmatter.containsKey("llm_model")) {
            String llmModel = getStringOrDefault(frontmatter, "llm_model", null);
            if (llmModel != null && !llmModel.isBlank()) {
                config.setLlmModel(llmModel.trim());
            }
        }

        // Parse optional per-agent LLM model overrides
        if (frontmatter.containsKey("llm_models")) {
            config.setLlmAgentModels(parseLlmAgentModels(frontmatter));
        }

        // Parse optional LLM temperature override
        if (frontmatter.containsKey("llm_temperature")) {
            double temp = getDoubleOrDefault(frontmatter, "llm_temperature", -1);
            if (temp < 0.0 || temp > 2.0) {
                throw new IllegalArgumentException(
                    "llm_temperature must be between 0.0 and 2.0, got: " + temp
                );
            }
            config.setLlmTemperature(temp);
        }

        // Parse optional agents list (subset of specialized agents to enable)
        if (frontmatter.containsKey("agents")) {
            config.setEnabledAgents(parseAgentList(frontmatter));
        }

        // Parse optional position cooldown (e.g., "14d", "2w", "12h")
        if (frontmatter.containsKey("position_cooldown")) {
            String cooldownStr = getStringOrDefault(frontmatter, "position_cooldown", null);
            if (cooldownStr != null && !cooldownStr.isBlank()) {
                config.setPositionCooldown(DurationParser.parse(cooldownStr.trim()));
            }
        }

        // Parse optional portfolio snapshot interval (e.g., "6h", "4h", "1d")
        if (frontmatter.containsKey("portfolio_snapshot_interval")) {
            String intervalStr = getStringOrDefault(frontmatter, "portfolio_snapshot_interval", null);
            if (intervalStr != null && !intervalStr.isBlank()) {
                config.setPortfolioSnapshotInterval(DurationParser.parse(intervalStr.trim()));
            }
        }

        // Store the markdown body as the strategy prompt
        config.setStrategyPrompt(markdownBody);

        // Validate the complete config before returning
        validateStrategyConfig(config);

        return config;
    }

    // =====================================================================
    // Frontmatter key surface
    // =====================================================================

    /**
     * Every top-level frontmatter key this parser acts on.
     *
     * <p>This list is the strategy format's contract. Anything outside it is rejected rather than
     * ignored, because ignoring it is indistinguishable from honouring it: a strategy declaring
     * {@code backtest_schedual: {interval: 1d}} — one transposed letter — was accepted in full and
     * then silently backtested at the 1-week default, producing one consensus decision where the
     * author expected seven, with nothing in the logs to explain the gap.</p>
     *
     * <p>Keep in step with the format documented in {@code GuideContent.STRATEGY_FORMAT}, the
     * strategy-designer prompt and the frontend template — {@code StrategyFormatContractTest}
     * parses every example in those three and fails if they drift apart.</p>
     */
    public static final Set<String> KNOWN_FRONTMATTER_KEYS = Set.of(
        "version",
        "consensus_method",
        "consensus_threshold",
        "reserve_cash_percentage",
        "max_order_fraction",
        "stock_tickers",
        "crypto_tickers",
        "live_schedule",
        "backtest_schedule",
        "agents",
        "llm_model",
        "llm_models",
        "llm_temperature",
        "portfolio_snapshot_interval",
        "position_cooldown",
        "position_limits"
    );

    /**
     * Removed keys that still get their own migration message from
     * {@link #rejectLegacyScheduleFields}. Listed here so the unknown-key check defers to it.
     */
    private static final Set<String> LEGACY_FRONTMATTER_KEYS = Set.of(
        "market_hours",
        "evaluation_interval_seconds"
    );

    /**
     * Reject frontmatter keys the parser does not act on, naming the likely intended key.
     */
    private void rejectUnknownFrontmatterKeys(Map<String, Object> frontmatter) {
        List<String> unknown = frontmatter.keySet().stream()
            .filter(key -> !KNOWN_FRONTMATTER_KEYS.contains(key))
            .filter(key -> !LEGACY_FRONTMATTER_KEYS.contains(key))
            .sorted()
            .toList();
        if (unknown.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder("Unrecognised frontmatter key");
        message.append(unknown.size() > 1 ? "s " : " ");
        message.append(unknown.stream().map(k -> "'" + k + "'").collect(Collectors.joining(", ")));
        message.append(". ");
        for (String key : unknown) {
            String suggestion = closestKnownKey(key);
            if (suggestion != null) {
                message.append("Did you mean '").append(suggestion).append("' instead of '")
                       .append(key).append("'? ");
            }
        }
        message.append("Valid keys: ")
               .append(KNOWN_FRONTMATTER_KEYS.stream().sorted().collect(Collectors.joining(", ")))
               .append(". Anything else is not read, so leaving it in would look like it applied.");
        throw new IllegalArgumentException(message.toString());
    }

    /** The known key nearest to {@code candidate}, or null when nothing is close enough. */
    private String closestKnownKey(String candidate) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String known : KNOWN_FRONTMATTER_KEYS) {
            int distance = editDistance(candidate.toLowerCase(), known);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = known;
            }
        }
        // A third of the key length, capped — close enough to catch a typo, far enough from
        // proposing 'agents' for a deliberate key the author invented.
        int tolerance = Math.min(4, Math.max(1, candidate.length() / 3));
        return bestDistance <= tolerance ? best : null;
    }

    private static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    // =====================================================================
    // Dual schedule parsing (live + backtest)
    // =====================================================================

    private static final int DEFAULT_BACKTEST_INTERVAL_SECONDS = 604800; // 1 week
    private static final int DEFAULT_CRYPTO_LIVE_INTERVAL_SECONDS = 300;

    /**
     * Parse live_schedule / backtest_schedule.
     *
     * <p>Either key on its own is enough to enter schedule parsing. Declaring only a
     * {@code backtest_schedule} is legitimate — a strategy you only ever backtest has no live
     * cadence to state — and used to be dropped on the floor: the parser keyed on
     * {@code live_schedule} alone, so a lone {@code backtest_schedule} fell through to the
     * defaults branch and every backtest silently ran at the 1-week default.</p>
     *
     * <p>The legacy {@code market_hours} / {@code evaluation_interval_seconds} fields are no
     * longer supported: stored strategies were rewritten to {@code live_schedule} by Flyway
     * migration V122, and any remaining legacy input is rejected with a clear error.</p>
     */
    private void parseSchedules(Map<String, Object> frontmatter, StrategyConfig config, TradingMode tradingMode) {
        rejectLegacyScheduleFields(frontmatter);

        boolean hasNewFormat = frontmatter.containsKey("live_schedule")
            || frontmatter.containsKey("backtest_schedule");

        if (hasNewFormat) {
            parseNewScheduleFormat(frontmatter, config, tradingMode);
        } else {
            // No schedule keys at all - apply defaults
            if (tradingMode == TradingMode.STOCK) {
                throw new IllegalArgumentException(
                    "STOCK trading mode requires 'live_schedule' configuration in frontmatter"
                );
            } else {
                // Crypto with no interval specified: default to 300s live, 1w backtest
                config.setLiveIntervalSeconds(DEFAULT_CRYPTO_LIVE_INTERVAL_SECONDS);
                config.setBacktestIntervalSeconds(DEFAULT_BACKTEST_INTERVAL_SECONDS);
            }
        }
    }

    /**
     * Reject the removed legacy schedule frontmatter fields with an actionable error.
     * Stored strategies were migrated to {@code live_schedule} by Flyway migration
     * V122__Migrate_legacy_schedule_frontmatter; new content must use the new format.
     */
    private void rejectLegacyScheduleFields(Map<String, Object> frontmatter) {
        List<String> legacyKeys = new ArrayList<>();
        if (frontmatter.containsKey("market_hours")) {
            legacyKeys.add("market_hours");
        }
        if (frontmatter.containsKey("evaluation_interval_seconds")) {
            legacyKeys.add("evaluation_interval_seconds");
        }
        if (!legacyKeys.isEmpty()) {
            throw new IllegalArgumentException(
                "Legacy schedule field(s) " + legacyKeys + " are no longer supported. "
                + "Use 'live_schedule' instead: for stocks replace 'market_hours:' with 'live_schedule:' "
                + "(same nested fields); for crypto replace 'evaluation_interval_seconds: <N>' with "
                + "'live_schedule:\\n  interval: <N>'. Stored strategies were migrated automatically "
                + "by database migration V122__Migrate_legacy_schedule_frontmatter."
            );
        }
    }

    /**
     * Parse the new live_schedule / backtest_schedule format.
     *
     * <p>{@code live_schedule} may be absent when only a {@code backtest_schedule} was declared.
     * Crypto then falls back to the default live cadence; stocks cannot, because
     * {@code live_schedule} is what carries the market hours a stock evaluation is clamped to.</p>
     */
    @SuppressWarnings("unchecked")
    private void parseNewScheduleFormat(Map<String, Object> frontmatter, StrategyConfig config, TradingMode tradingMode) {
        Map<String, Object> liveMap = null;
        if (frontmatter.containsKey("live_schedule")) {
            Object liveObj = frontmatter.get("live_schedule");
            if (!(liveObj instanceof Map)) {
                throw new IllegalArgumentException("live_schedule must be a YAML object/map");
            }
            liveMap = (Map<String, Object>) liveObj;
        } else if (tradingMode == TradingMode.STOCK) {
            throw new IllegalArgumentException(
                "STOCK trading mode requires 'live_schedule' configuration in frontmatter"
            );
        }

        if (tradingMode == TradingMode.STOCK) {
            // STOCK: parse live_schedule as MarketHours
            MarketHours liveSchedule = parseMarketHoursFromMap(liveMap);
            config.setLiveSchedule(liveSchedule);
            config.setLiveIntervalSeconds(0);

            // Parse backtest_schedule or generate defaults
            if (frontmatter.containsKey("backtest_schedule")) {
                Object btObj = frontmatter.get("backtest_schedule");
                if (!(btObj instanceof Map)) {
                    throw new IllegalArgumentException("backtest_schedule must be a YAML object/map");
                }
                MarketHours backtestSchedule = parseMarketHoursFromMap((Map<String, Object>) btObj);
                config.setBacktestSchedule(backtestSchedule);
            } else {
                config.setBacktestSchedule(generateDefaultStockBacktestSchedule(liveSchedule));
            }
            config.setBacktestIntervalSeconds(0);
        } else {
            // CRYPTO: parse live_schedule.interval, or fall back to the default cadence when the
            // author declared only a backtest_schedule.
            int liveSeconds = liveMap != null
                ? parseIntervalFromScheduleMap(liveMap, "live_schedule")
                : DEFAULT_CRYPTO_LIVE_INTERVAL_SECONDS;
            config.setLiveIntervalSeconds(liveSeconds);
            config.setLiveSchedule(null);

            // Parse backtest_schedule or default to 1w
            if (frontmatter.containsKey("backtest_schedule")) {
                Object btObj = frontmatter.get("backtest_schedule");
                if (!(btObj instanceof Map)) {
                    throw new IllegalArgumentException("backtest_schedule must be a YAML object/map");
                }
                int btSeconds = parseIntervalFromScheduleMap((Map<String, Object>) btObj, "backtest_schedule");
                config.setBacktestIntervalSeconds(btSeconds);
            } else {
                config.setBacktestIntervalSeconds(DEFAULT_BACKTEST_INTERVAL_SECONDS);
            }
            config.setBacktestSchedule(null);
        }
    }

    /**
     * Parse the {@code interval} field from a schedule map.
     * Supports both human-readable strings (e.g., "1h") and legacy {@code interval_seconds} integer.
     */
    private int parseIntervalFromScheduleMap(Map<String, Object> map, String context) {
        // New format: interval as human-readable string
        if (map.containsKey("interval")) {
            Object intervalObj = map.get("interval");
            String intervalStr = intervalObj.toString().trim();
            // Could be a bare integer from YAML (e.g., interval: 3600)
            try {
                int numericValue = Integer.parseInt(intervalStr);
                return numericValue;
            } catch (NumberFormatException ignored) {
                // Not numeric, parse as human-readable duration
            }
            return DurationParser.parseToSeconds(intervalStr);
        }

        // Fallback: interval_seconds
        if (map.containsKey("interval_seconds")) {
            return getIntOrDefault(map, "interval_seconds", 300);
        }

        throw new IllegalArgumentException(
            context + " must contain either 'interval' (e.g., '1h') or 'interval_seconds' (e.g., 3600)"
        );
    }

    /**
     * Generate the default backtest schedule for STOCK mode: the live schedule thinned to a
     * single weekly evaluation, so a backtest costs a fraction of the LLM calls.
     *
     * <p>The day and time are taken <b>from the live schedule</b> rather than hard-coded.
     * The old version copied the live timezone but pinned {@code WEDNESDAY 10:00}, which is an
     * Eastern-shaped literal: for {@code Europe/Amsterdam} it is 04:00 ET, so the default was
     * rejected by the venue check — and a live window that does not contain 10:00 failed the
     * {@link MarketHours} constructor first, complaining about a time the user never wrote.
     * A field documented as optional therefore broke every non-Eastern user, including one
     * following the guide's own worked Paris example.</p>
     *
     * <p>Inheriting from the live schedule cannot self-invalidate: those times already sit
     * inside the user's window and already passed the venue check.</p>
     */
    private MarketHours generateDefaultStockBacktestSchedule(MarketHours liveSchedule) {
        // Keep the weekly Wednesday cadence when the user actually trades that day; otherwise
        // fall back to their first trading day rather than inventing one they never declared.
        DayOfWeek day = liveSchedule.tradingDays().contains(DayOfWeek.WEDNESDAY)
            ? DayOfWeek.WEDNESDAY
            : liveSchedule.tradingDays().get(0);

        return new MarketHours(
            liveSchedule.timezone(),
            List.of(day),
            List.of(liveSchedule.evaluationTimes().get(0)),
            liveSchedule.marketOpen(),
            liveSchedule.marketClose()
        );
    }

    /**
     * Parse a MarketHours value object from a YAML map.
     * Reusable for both live_schedule and backtest_schedule parsing.
     */
    private MarketHours parseMarketHoursFromMap(Map<String, Object> scheduleMap) {
        String timezoneStr = getStringOrDefault(scheduleMap, "timezone", "America/New_York");
        ZoneId timezone;
        try {
            timezone = ZoneId.of(timezoneStr);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timezone: " + timezoneStr, e);
        }

        List<DayOfWeek> tradingDays = parseTradingDays(scheduleMap);
        List<LocalTime> evaluationTimes = parseEvaluationTimes(scheduleMap);
        if (evaluationTimes.isEmpty()) {
            throw new IllegalArgumentException("evaluation_times is required and cannot be empty");
        }
        LocalTime marketOpen = parseTime(scheduleMap, "market_open", LocalTime.of(9, 30));
        LocalTime marketClose = parseTime(scheduleMap, "market_close", LocalTime.of(16, 0));

        return new MarketHours(timezone, tradingDays, evaluationTimes, marketOpen, marketClose);
    }

    /**
     * Validates a complete StrategyConfig for business rule compliance.
     * Throws StrategyValidationException with all validation errors if invalid.
     *
     * @param config The config to validate
     * @throws StrategyValidationException if validation fails
     */
    private void validateStrategyConfig(StrategyConfig config) {
        List<String> errors = new ArrayList<>();

        // Must have at least one ticker
        boolean hasCryptoTickers = config.getCryptoTickers() != null && !config.getCryptoTickers().isEmpty();
        boolean hasStockTickers = config.getStockTickers() != null && !config.getStockTickers().isEmpty();
        if (!hasCryptoTickers && !hasStockTickers) {
            errors.add("Strategy must specify at least one ticker (crypto_tickers or stock_tickers)");
        }

        // Stock mode requires live schedule with evaluation_times.
        //
        // A basket holding both stock and crypto tickers is inferred as STOCK, because the
        // market-hours constraint of the equities is the binding one. That inference is silent,
        // so an author who wrote a crypto-style `interval` schedule for a mixed basket was told
        // only that evaluation_times was missing, with no hint as to why a crypto strategy
        // suddenly needed stock-market opening times. Say which tickers forced the mode.
        boolean mixedBasket = hasCryptoTickers && hasStockTickers;
        if (config.getTradingMode() == TradingMode.STOCK) {
            String mixedNote = mixedBasket
                ? " This strategy mixes stock tickers " + tickerNames(config.getStockTickers())
                  + " with crypto tickers " + tickerNames(config.getCryptoTickers())
                  + ", so it runs on the stock schedule — crypto trades only while the equity"
                  + " market is open. Split them into separate strategies to trade crypto 24/7."
                : "";
            if (config.getLiveSchedule() == null) {
                errors.add("Stock mode requires live_schedule configuration." + mixedNote);
            } else if (config.getLiveSchedule().evaluationTimes().isEmpty()) {
                errors.add("live_schedule.evaluation_times is required for stock mode." + mixedNote);
            }
        }

        // Bounds validation
        if (config.getConsensusThreshold() < 0.0 || config.getConsensusThreshold() > 1.0) {
            errors.add("consensus_threshold must be between 0.0 and 1.0, got: " + config.getConsensusThreshold());
        }
        if (config.getReserveCashPercentage() < 0.0 || config.getReserveCashPercentage() > 1.0) {
            errors.add("reserve_cash_percentage must be between 0.0 and 1.0, got: " + config.getReserveCashPercentage());
        }
        if (config.getMaxOrderFraction() != null
                && (config.getMaxOrderFraction() <= 0.0 || config.getMaxOrderFraction() > 1.0)) {
            errors.add("max_order_fraction must be greater than 0.0 and at most 1.0, got: "
                + config.getMaxOrderFraction() + ". Omit the field to let the platform size orders"
                + " (25% of portfolio value per order when the agents give no quantity).");
        }

        // Position limits validation (if present)
        if (config.getPositionLimits() != null) {
            for (Map.Entry<String, Double> entry : config.getPositionLimits().entrySet()) {
                if (entry.getValue() < 0.0 || entry.getValue() > 1.0) {
                    errors.add("position_limits." + entry.getKey() + " must be between 0.0 and 1.0, got: " + entry.getValue());
                }
            }
        }

        // Crypto mode requires positive live interval
        if (config.getTradingMode() == TradingMode.CRYPTO && config.getLiveIntervalSeconds() <= 0) {
            errors.add("Live interval must be positive for crypto mode, got: " + config.getLiveIntervalSeconds());
        }

        // Agents validation (if explicitly set)
        if (config.getEnabledAgents() != null) {
            if (config.getEnabledAgents().isEmpty()) {
                errors.add("agents list cannot be empty — omit the field to use all agents");
            }
            if (config.getEnabledAgents().contains(AgentRole.ORCHESTRATOR)) {
                errors.add("ORCHESTRATOR should not be listed in agents — it always runs automatically");
            }
        }

        if (!errors.isEmpty()) {
            throw new StrategyValidationException(errors);
        }
    }

    // Helper methods for type-safe extraction from YAML map

    private String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private double getDoubleOrDefault(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        return value != null ? convertToDouble(value) : defaultValue;
    }

    private int getIntOrDefault(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private boolean getBooleanOrDefault(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    private double convertToDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Parse {@code consensus_method}, accepting the documented aliases.
     *
     * <p>An unrecognised value is rejected rather than coerced. It used to fall back to
     * {@code MAJORITY} — which is not even the default for an absent key ({@code
     * ORCHESTRATOR_OVERRIDE}), so a single typo silently swapped one decision system for another at
     * a different cost per evaluation, while the read APIs kept echoing the typo back as though it
     * were in effect.</p>
     */
    private ConsensusMethod parseConsensusMethod(String method) {
        // Handle common aliases and legacy values before enum lookup
        String normalized = method.toUpperCase().trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                "consensus_method is present but blank. Set MAJORITY or ORCHESTRATOR_OVERRIDE, "
                + "or omit the field to use ORCHESTRATOR_OVERRIDE."
            );
        }
        return switch (normalized) {
            case "MAJORITY_VOTE" -> ConsensusMethod.MAJORITY;
            case "WEIGHTED", "WEIGHTED_AVERAGE", "CONFIDENCE_BASED" -> ConsensusMethod.MAJORITY;
            default -> {
                try {
                    yield ConsensusMethod.valueOf(normalized);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                        "Unknown consensus_method '" + method.trim() + "'. Valid values: "
                        + "MAJORITY (agent votes only) or ORCHESTRATOR_OVERRIDE (an orchestrator LLM "
                        + "decides, at the cost of one extra call per evaluation). Omit the field to "
                        + "use ORCHESTRATOR_OVERRIDE."
                    );
                }
            }
        };
    }

    /**
     * Parse agent list from YAML frontmatter.
     * Accepts a list of agent role names (TECHNICAL, FUNDAMENTAL, SENTIMENT).
     * ORCHESTRATOR cannot be listed (it always runs).
     *
     * @param frontmatter The parsed YAML frontmatter
     * @return Set of AgentRole values
     * @throws IllegalArgumentException if list is empty, contains ORCHESTRATOR, or has invalid values
     */
    private Set<AgentRole> parseAgentList(Map<String, Object> frontmatter) {
        Object value = frontmatter.get("agents");
        if (value == null) {
            return null;
        }

        if (!(value instanceof List)) {
            throw new IllegalArgumentException("agents must be a list of agent role names");
        }

        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) value;

        if (list.isEmpty()) {
            throw new IllegalArgumentException("agents list cannot be empty — omit the field to use all agents");
        }

        Set<AgentRole> agents = new LinkedHashSet<>();
        for (Object item : list) {
            String roleName = extractAgentRoleName(item);

            if ("ORCHESTRATOR".equals(roleName)) {
                // Orchestrator always runs automatically — skip silently
                continue;
            }

            try {
                agents.add(AgentRole.valueOf(roleName));
            } catch (IllegalArgumentException e) {
                // Rejected, not skipped. Skipping meant a typo'd role vanished, and if it was the
                // only entry the strategy fell through to getEffectiveEnabledAgents()' all-three
                // default — so `agents: [TECHNCIAL]` quietly ran three agents and billed three
                // times what the author asked for.
                throw new IllegalArgumentException(
                    "Unknown agent role '" + roleName + "' in agents. Valid roles: "
                    + Arrays.stream(AgentRole.values())
                        .filter(r -> r != AgentRole.ORCHESTRATOR)
                        .map(Enum::name)
                        .collect(Collectors.joining(", "))
                    + ". ORCHESTRATOR is not listed — it runs automatically under "
                    + "ORCHESTRATOR_OVERRIDE. Omit the field entirely to use all agents."
                );
            }
        }

        // Only reachable when the list held nothing but ORCHESTRATOR, which is skipped above.
        return agents.isEmpty() ? null : agents;
    }

    /**
     * Parse per-agent LLM model overrides from the llm_models YAML key.
     * Expected format:
     * <pre>
     * llm_models:
     *   TECHNICAL: "google/gemini-2.5-flash"
     *   SENTIMENT: "anthropic/claude-sonnet-4.6"
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private Map<AgentRole, String> parseLlmAgentModels(Map<String, Object> frontmatter) {
        Object value = frontmatter.get("llm_models");
        if (value == null) {
            return null;
        }

        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("llm_models must be a map of agent role to model ID");
        }

        Map<String, Object> map = (Map<String, Object>) value;
        if (map.isEmpty()) {
            return null;
        }

        Map<AgentRole, String> agentModels = new EnumMap<>(AgentRole.class);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String roleName = entry.getKey().trim().toUpperCase();
            String modelId = entry.getValue() != null ? entry.getValue().toString().trim() : "";

            if (modelId.isBlank()) {
                throw new IllegalArgumentException(
                    "llm_models: model ID for '" + roleName + "' cannot be blank"
                );
            }

            try {
                AgentRole role = AgentRole.valueOf(roleName);
                agentModels.put(role, modelId);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    "llm_models: invalid agent role '" + roleName + "'. Valid values: TECHNICAL, FUNDAMENTAL, SENTIMENT, ORCHESTRATOR"
                );
            }
        }

        return agentModels;
    }

    /**
     * Extract agent role name from either a plain string or a map with a TYPE key.
     * Supports two YAML formats:
     * <pre>
     * agents:
     *   - TECHNICAL                    # plain string
     *   - TYPE: TECHNICAL              # map with TYPE key
     *     WEIGHT: 0.3
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private String extractAgentRoleName(Object item) {
        if (item instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) item;
            Object type = map.get("TYPE");
            if (type == null) {
                type = map.get("type");
            }
            if (type == null) {
                throw new IllegalArgumentException(
                    "Agent map entry must have a 'TYPE' key, got: " + map.keySet()
                );
            }
            return type.toString().trim().toUpperCase();
        }
        return item.toString().trim().toUpperCase();
    }

    /**
     * Parse a list of ticker symbols from YAML frontmatter and convert to Symbol objects.
     *
     * <p>Each entry may be either:</p>
     * <ul>
     *   <li>A plain string token — a bare US ticker ({@code AAPL}), an
     *       exchange-qualified canonical EU form ({@code RHM:XETR}), or a dotted EU
     *       suffix form ({@code RHM.DE}). US bare tickers continue to parse exactly
     *       as before (the 2-arg {@link Symbol} path, byte-identical canonical form),
     *       while exchange-qualified tokens resolve to an enriched {@link Symbol}
     *       carrying its {@link com.quorumtrading.model.valueobject.MIC} and
     *       settlement currency.</li>
     *   <li>A YAML map carrying an explicit currency, e.g.
     *       {@code {ticker: RHM:XETR, currency: EUR}} — the {@code ticker} field is
     *       parsed as above and the {@code currency} overrides the venue default.</li>
     * </ul>
     *
     * @param frontmatter The parsed YAML frontmatter
     * @param key The key to look up (e.g., "stock_tickers", "crypto_tickers")
     * @param type The investment type to assign (STOCK or CRYPTO)
     * @return Set of Symbol objects with specified type, or empty set if not found
     */
    /** Comma-separated ticker list for diagnostics, e.g. "AAPL, MSFT". */
    private static String tickerNames(Set<Symbol> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return "(none)";
        }
        return symbols.stream().map(Symbol::ticker).sorted().collect(java.util.stream.Collectors.joining(", "));
    }

    private Set<Symbol> parseTickerList(Map<String, Object> frontmatter, String key, InvestmentType type) {
        if (!frontmatter.containsKey(key)) {
            return new HashSet<>();
        }

        Object value = frontmatter.get(key);
        Set<Symbol> symbols = new HashSet<>();

        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            for (Object item : list) {
                Symbol parsed = parseTickerEntry(item, type);
                if (parsed != null) {
                    symbols.add(parsed);
                }
            }
        } else {
            // Handle single ticker (plain string or map) provided directly
            Symbol parsed = parseTickerEntry(value, type);
            if (parsed != null) {
                symbols.add(parsed);
            }
        }

        return symbols;
    }

    /**
     * Parse a single ticker entry — either a plain string token or a YAML map with
     * a {@code ticker} (and optional {@code currency}) field — into a {@link Symbol}
     * of the given investment type.
     *
     * @return the parsed Symbol, or {@code null} if the entry has no usable ticker
     */
    private Symbol parseTickerEntry(Object item, InvestmentType type) {
        if (item == null) {
            return null;
        }

        String rawToken;
        Money.Currency explicitCurrency = null;

        if (item instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) item;
            Object tickerVal = map.get("ticker");
            if (tickerVal == null) {
                tickerVal = map.get("symbol");
            }
            if (tickerVal == null) {
                return null;
            }
            rawToken = tickerVal.toString();
            Object currencyVal = map.get("currency");
            if (currencyVal != null && !currencyVal.toString().isBlank()) {
                explicitCurrency = parseCurrency(currencyVal.toString());
            }
        } else {
            rawToken = item.toString();
        }

        return parseSymbolToken(rawToken, type, explicitCurrency);
    }

    /**
     * Convert a single ticker token into a {@link Symbol} of the given investment
     * type, reusing {@link Symbol#parse(String)} / {@link MIC} so the canonical form
     * matches the rest of the system.
     *
     * <p>Resolution rules:</p>
     * <ul>
     *   <li><b>Bare US ticker</b> (no exchange qualifier, e.g. {@code AAPL}) →
     *       {@code new Symbol(ticker, type)} — the legacy 2-arg path, byte-identical
     *       to the previous behaviour ({@code canonical() == ticker}, USD).</li>
     *   <li><b>Colon-qualified</b> ({@code RHM:XETR}) → parsed via {@link Symbol#parse}
     *       to extract the MIC, then re-stamped with the explicit investment type and
     *       a resolved settlement currency.</li>
     *   <li><b>Dotted EU suffix</b> ({@code RHM.DE}, {@code ASML.AS}) → the suffix is
     *       mapped to its MIC and processed through the same exchange-qualified path.</li>
     * </ul>
     *
     * @param rawToken the YAML ticker token
     * @param type the investment type (STOCK / CRYPTO) the list assigns
     * @param explicitCurrency a currency from the YAML entry, or {@code null} to use the venue default
     * @return the resolved Symbol, or {@code null} for a blank token
     */
    private Symbol parseSymbolToken(String rawToken, InvestmentType type, Money.Currency explicitCurrency) {
        if (rawToken == null) {
            return null;
        }
        String token = rawToken.trim().toUpperCase();
        if (token.isEmpty()) {
            return null;
        }

        // Canonical exchange-qualified form "ROOT:MIC".
        int colon = token.indexOf(':');
        if (colon >= 0) {
            return buildEuropeanSymbol(token.substring(0, colon), token.substring(colon + 1), type, explicitCurrency);
        }

        // Dotted EU suffix form ("RHM.DE") — only when the suffix maps to a known venue.
        int lastDot = token.lastIndexOf('.');
        if (lastDot > 0 && lastDot < token.length() - 1) {
            String suffix = token.substring(lastDot + 1);
            MIC mic = MIC_BY_SUFFIX.get(suffix);
            if (mic != null) {
                return buildEuropeanSymbol(token.substring(0, lastDot), mic.code(), type, explicitCurrency);
            }
        }

        // Bare US ticker — unchanged 2-arg path (byte-identical canonical form, USD).
        return new Symbol(token, type);
    }

    /**
     * Build an exchange-qualified Symbol from a root + MIC code, preserving the
     * explicitly-assigned investment type and resolving the settlement currency
     * (explicit currency wins, otherwise the venue's natural currency, otherwise USD).
     */
    private Symbol buildEuropeanSymbol(String root, String micCode, InvestmentType type, Money.Currency explicitCurrency) {
        // Reuse Symbol.parse() for MIC parsing/validation so the canonical form is
        // produced identically to the rest of the system; it infers a placeholder
        // type which we discard in favour of the list's explicit type below.
        Symbol parsed = Symbol.parse(root + ":" + micCode);
        MIC exchange = parsed.exchange();
        Money.Currency currency = explicitCurrency != null
            ? explicitCurrency
            : CURRENCY_BY_MIC.getOrDefault(micCode, Money.Currency.USD);
        return new Symbol(parsed.ticker(), type, exchange, currency, null, null);
    }

    /**
     * Map a currency code string to a {@link Money.Currency}. Unknown / unsupported
     * codes fall back to USD so a strategy never fails to parse over an exotic ccy.
     */
    private Money.Currency parseCurrency(String code) {
        try {
            return Money.Currency.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Money.Currency.USD;
        }
    }

    /**
     * Parse trading days from a schedule YAML map.
     * Accepts list of day names (MONDAY, TUESDAY, etc.) or abbreviations (MON, TUE, etc.).
     * Defaults to Monday-Friday if not specified.
     */
    private List<DayOfWeek> parseTradingDays(Map<String, Object> marketHoursMap) {
        if (!marketHoursMap.containsKey("trading_days")) {
            return List.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            );
        }

        Object value = marketHoursMap.get("trading_days");
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("trading_days must be a list");
        }

        @SuppressWarnings("unchecked")
        List<Object> daysList = (List<Object>) value;

        return daysList.stream()
            .map(Object::toString)
            .map(String::toUpperCase)
            .map(this::parseDayOfWeek)
            .collect(Collectors.toList());
    }

    /**
     * Parse a single day of week string, supporting full names and abbreviations.
     * Examples: "MONDAY", "MON", "Monday", "Mon"
     */
    private DayOfWeek parseDayOfWeek(String dayStr) {
        // Try full name first
        try {
            return DayOfWeek.valueOf(dayStr);
        } catch (IllegalArgumentException e) {
            // Try abbreviation
            return switch (dayStr.substring(0, Math.min(3, dayStr.length()))) {
                case "MON" -> DayOfWeek.MONDAY;
                case "TUE" -> DayOfWeek.TUESDAY;
                case "WED" -> DayOfWeek.WEDNESDAY;
                case "THU" -> DayOfWeek.THURSDAY;
                case "FRI" -> DayOfWeek.FRIDAY;
                case "SAT" -> DayOfWeek.SATURDAY;
                case "SUN" -> DayOfWeek.SUNDAY;
                default -> throw new IllegalArgumentException("Invalid day of week: " + dayStr);
            };
        }
    }

    /**
     * Parse evaluation times from a schedule YAML map.
     * Accepts list of time strings in HH:MM format (e.g., ["10:00", "14:00"]).
     * REQUIRED for STOCK mode.
     */
    private List<LocalTime> parseEvaluationTimes(Map<String, Object> marketHoursMap) {
        if (!marketHoursMap.containsKey("evaluation_times")) {
            throw new IllegalArgumentException(
                "live_schedule.evaluation_times is required for STOCK trading mode"
            );
        }

        Object value = marketHoursMap.get("evaluation_times");
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("evaluation_times must be a list");
        }

        @SuppressWarnings("unchecked")
        List<Object> timesList = (List<Object>) value;

        return timesList.stream()
            .map(Object::toString)
            .map(this::parseTimeString)
            .collect(Collectors.toList());
    }

    /**
     * Parse a time string in HH:MM format (e.g., "10:00", "14:30").
     */
    private LocalTime parseTimeString(String timeStr) {
        try {
            return LocalTime.parse(timeStr);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Invalid time format: " + timeStr + ". Expected HH:MM (e.g., '10:00')",
                e
            );
        }
    }

    /**
     * Parse a single time value from a schedule YAML map with default fallback.
     */
    private LocalTime parseTime(Map<String, Object> marketHoursMap, String key, LocalTime defaultValue) {
        if (!marketHoursMap.containsKey(key)) {
            return defaultValue;
        }

        String timeStr = marketHoursMap.get(key).toString();
        return parseTimeString(timeStr);
    }

    /**
     * Record to hold parsed frontmatter results for legacy format.
     */
    private record FrontmatterParseResult(String yaml, String markdown) {}

    /**
     * Parse legacy frontmatter format where the closing --- is missing.
     *
     * Detection strategy: YAML lines have a specific pattern (key: value or list items).
     * When we encounter a line that looks like prose/markdown (not YAML-like),
     * that marks the start of the markdown section.
     *
     * @param markdown The raw markdown input starting with ---
     * @return FrontmatterParseResult with separated YAML and markdown, or null if cannot parse
     */
    private FrontmatterParseResult parseLegacyFrontmatter(String markdown) {
        // Must start with ---
        if (!markdown.startsWith("---")) {
            return null;
        }

        // Skip the opening ---
        String afterOpening = markdown.substring(3).trim();
        String[] lines = afterOpening.split("\n");

        StringBuilder yamlBuilder = new StringBuilder();
        StringBuilder markdownBuilder = new StringBuilder();
        boolean foundMarkdownStart = false;

        // YAML line patterns - these indicate we're still in YAML section:
        // - "key: value" or "key:" (starts with word chars, followed by colon)
        // - "  - item" (list items with indentation)
        // - "  key: value" (indented key-value pairs)
        // - Empty lines (could be in either section, keep in current section)
        // - "- item" (list items without indentation)
        // - Lines that are just "[" or "]" or "{" or "}" (inline YAML)
        Pattern yamlLinePattern = Pattern.compile(
            "^\\s*(\\w[\\w_-]*:|" +          // key: (with optional value)
            "- [\\w\"\\[{]|" +               // - list item (start of item)
            "\\[|\\]|\\{|\\}|" +             // inline array/object markers
            "\".*\"|" +                      // quoted string
            "\\d+(\\.\\d+)?|" +              // numbers
            "true|false|null|" +             // YAML literals
            "timezone:|trading_days:|" +     // Known YAML keys
            "evaluation_times:|market_open:|market_close:|" +
            "position_limits:|reserve_cash_percentage:|" +
            "stock_tickers:|crypto_tickers:|" +
            "version:|consensus_method:|consensus_threshold:|" +
            "live_schedule:|backtest_schedule:|interval:|interval_seconds:|" +
            "llm_model:|llm_models:|llm_temperature:|agents:|position_cooldown:|" +
            "TECHNICAL:|FUNDAMENTAL:|SENTIMENT:|ORCHESTRATOR:).*$",
            Pattern.CASE_INSENSITIVE
        );

        for (String line : lines) {
            String trimmedLine = line.trim();

            if (foundMarkdownStart) {
                markdownBuilder.append(line).append("\n");
            } else if (trimmedLine.isEmpty()) {
                // Empty lines: keep in YAML until we find markdown start
                yamlBuilder.append(line).append("\n");
            } else if (yamlLinePattern.matcher(trimmedLine).matches()) {
                // This looks like YAML
                yamlBuilder.append(line).append("\n");
            } else {
                // This doesn't look like YAML - start of markdown/prose content
                foundMarkdownStart = true;
                markdownBuilder.append(line).append("\n");
            }
        }

        String yaml = yamlBuilder.toString().trim();
        String markdownContent = markdownBuilder.toString().trim();

        // Validate we found YAML content
        if (yaml.isEmpty()) {
            return null;
        }

        return new FrontmatterParseResult(yaml, markdownContent);
    }
}
