package com.quorumtrading.adapter.persistence.migration;

import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites legacy schedule frontmatter in stored strategy markdown to the
 * {@code live_schedule} format. Pure text transformation used by the Flyway
 * Java migration {@code V122__Migrate_legacy_schedule_frontmatter}.
 *
 * <p><b>Lossless mapping</b> (mirrors the removed legacy branch of
 * {@code StrategyConfigParser.parseLegacyScheduleFormat}):</p>
 * <ul>
 *   <li><b>STOCK mode</b> (non-empty {@code stock_tickers}): {@code market_hours:} is renamed to
 *       {@code live_schedule:} — the nested fields (timezone, trading_days, evaluation_times,
 *       market_open, market_close) are identical in both formats. A legacy
 *       {@code evaluation_interval_seconds} line is dropped (it was ignored in stock mode).
 *       The default backtest schedule (Wednesday 10:00) is generated identically for both
 *       formats, so omitting {@code backtest_schedule} stays equivalent.</li>
 *   <li><b>CRYPTO mode</b>: {@code evaluation_interval_seconds: N} becomes
 *       {@code live_schedule:\n  interval: N} (bare integers are parsed as seconds by the new
 *       format). A non-numeric legacy value fell back to 300 seconds, so it is migrated to
 *       {@code interval: 300}. A stray {@code market_hours:} block is dropped (it was ignored
 *       in crypto mode); if it was the only schedule key, removing it is equivalent because
 *       both the legacy path and the no-schedule default resolve to a 300s live interval.
 *       The default backtest interval (1w) is identical in both formats.</li>
 *   <li>If {@code live_schedule} is already present, legacy keys were dead configuration —
 *       they are simply removed.</li>
 * </ul>
 *
 * <p>Idempotent: content without legacy keys (including this migrator's own output) is
 * left untouched.</p>
 */
public final class LegacyScheduleFrontmatterMigrator {

    /** Same frontmatter shape the parser requires: ---\nYAML\n---\nbody. */
    private static final Pattern FRONTMATTER_PATTERN =
        Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$", Pattern.DOTALL);

    /** Legacy crypto default when evaluation_interval_seconds carried a non-numeric value. */
    private static final int LEGACY_DEFAULT_INTERVAL_SECONDS = 300;

    private LegacyScheduleFrontmatterMigrator() {
    }

    /**
     * Migrate legacy schedule frontmatter to the live_schedule format.
     *
     * @param content the stored strategy markdown
     * @return the rewritten markdown, or empty if no change is needed (or the content
     *         cannot be safely rewritten — e.g. unparseable frontmatter)
     */
    @SuppressWarnings("unchecked")
    public static Optional<String> migrate(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }

        boolean hadBom = content.startsWith("\uFEFF");
        String noBom = hadBom ? content.substring(1) : content;

        Matcher matcher = FRONTMATTER_PATTERN.matcher(noBom);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String frontmatter = matcher.group(1);

        // Fast path: no legacy tokens at all
        if (!frontmatter.contains("market_hours") && !frontmatter.contains("evaluation_interval_seconds")) {
            return Optional.empty();
        }

        Map<String, Object> yamlMap;
        try {
            Object loaded = new Yaml().load(frontmatter);
            if (!(loaded instanceof Map)) {
                return Optional.empty();
            }
            yamlMap = (Map<String, Object>) loaded;
        } catch (Exception e) {
            return Optional.empty();
        }

        boolean hasMarketHours = yamlMap.containsKey("market_hours");
        boolean hasLegacyInterval = yamlMap.containsKey("evaluation_interval_seconds");
        if (!hasMarketHours && !hasLegacyInterval) {
            return Optional.empty();
        }

        boolean hasLiveSchedule = yamlMap.containsKey("live_schedule");
        boolean stockMode = hasNonEmptyTickers(yamlMap.get("stock_tickers"));

        String[] lines = frontmatter.split("\n", -1);
        List<String> out = new ArrayList<>(lines.length);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (hasMarketHours && isTopLevelKey(line, "market_hours")) {
                if (!hasLiveSchedule && stockMode) {
                    // Rename the key; the nested block is format-identical.
                    out.add(line.replaceFirst("^market_hours\\s*:", "live_schedule:"));
                } else {
                    // Dead configuration (crypto mode, or live_schedule already present): drop block.
                    i = skipNestedBlock(lines, i);
                }
                continue;
            }
            if (hasLegacyInterval && isTopLevelKey(line, "evaluation_interval_seconds")) {
                if (!hasLiveSchedule && !stockMode) {
                    int seconds = legacyIntervalSeconds(yamlMap.get("evaluation_interval_seconds"));
                    out.add("live_schedule:");
                    out.add("  interval: " + seconds);
                }
                // Stock mode / live_schedule present: the legacy interval was ignored — drop it.
                continue;
            }
            out.add(line);
        }

        String newFrontmatter = String.join("\n", out);
        if (newFrontmatter.equals(frontmatter)) {
            return Optional.empty();
        }

        String rebuilt = noBom.substring(0, matcher.start(1))
            + newFrontmatter
            + noBom.substring(matcher.end(1));
        return Optional.of(hadBom ? "\uFEFF" + rebuilt : rebuilt);
    }

    /**
     * True when the content has standard frontmatter that still contains legacy schedule
     * tokens. Used by the migration to log strategies it could not rewrite.
     */
    public static boolean frontmatterContainsLegacyKeys(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String noBom = content.startsWith("\uFEFF") ? content.substring(1) : content;
        Matcher matcher = FRONTMATTER_PATTERN.matcher(noBom);
        String scope = matcher.find() ? matcher.group(1) : noBom;
        return scope.contains("market_hours:") || scope.contains("evaluation_interval_seconds:");
    }

    /** A top-level (column 0) {@code key:} line, tolerating a trailing \r and comments. */
    private static boolean isTopLevelKey(String line, String key) {
        if (!line.startsWith(key)) {
            return false;
        }
        String rest = line.substring(key.length()).replace("\r", "");
        return rest.stripLeading().startsWith(":");
    }

    /**
     * Return the index of the last line belonging to the nested block started at
     * {@code keyIdx}. Consumes indented lines; blank lines are consumed only when a
     * later indented line follows (so trailing blank separators are preserved).
     */
    private static int skipNestedBlock(String[] lines, int keyIdx) {
        int i = keyIdx;
        while (i + 1 < lines.length) {
            String next = lines[i + 1];
            if (next.replace("\r", "").isBlank()) {
                int k = i + 2;
                while (k < lines.length && lines[k].replace("\r", "").isBlank()) {
                    k++;
                }
                if (k < lines.length && isIndented(lines[k])) {
                    i = k;
                    continue;
                }
                break;
            }
            if (isIndented(next)) {
                i++;
                continue;
            }
            break;
        }
        return i;
    }

    private static boolean isIndented(String line) {
        return !line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t');
    }

    /**
     * Legacy semantics: only YAML numbers were honoured
     * ({@code getIntOrDefault(..., 300)}); anything else fell back to 300.
     */
    private static int legacyIntervalSeconds(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return LEGACY_DEFAULT_INTERVAL_SECONDS;
    }

    /** Mirrors the parser's mode inference: STOCK iff stock_tickers has a usable entry. */
    private static boolean hasNonEmptyTickers(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (isUsableTickerEntry(item)) {
                    return true;
                }
            }
            return false;
        }
        return isUsableTickerEntry(value);
    }

    private static boolean isUsableTickerEntry(Object item) {
        if (item == null) {
            return false;
        }
        if (item instanceof Map<?, ?> map) {
            Object ticker = map.get("ticker");
            if (ticker == null) {
                ticker = map.get("symbol");
            }
            return ticker != null && !ticker.toString().isBlank();
        }
        return !item.toString().isBlank();
    }
}
