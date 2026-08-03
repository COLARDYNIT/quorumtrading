package com.quorumtrading.adapter.mcp.resource;

import com.quorumtrading.model.orchestration.StrategyConfigParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The strategy format is taught in four places; none of them may teach a key the parser ignores.
 *
 * <p>This is the drift guard for the class of defect that dominated the MCP dog-fooding sweep:
 * documentation promising something the code does not do. A key that appears in a guide, a prompt
 * or the UI template but not in {@link StrategyConfigParser#KNOWN_FRONTMATTER_KEYS} is worse than a
 * missing feature — the author writes it, the parser skips it, and the strategy runs on defaults
 * that nobody chose. That is exactly how a {@code backtest_schedule} asking for a daily cadence
 * came to run weekly.</p>
 *
 * <p>CLAUDE.md already names the strategy-format sources as a keep-in-sync set. Nothing enforced
 * it; this does. If you add a frontmatter key, add it to {@code KNOWN_FRONTMATTER_KEYS} and this
 * test goes green — if you document one without implementing it, it does not.</p>
 */
class StrategyFormatContractTest {

    /**
     * A zero-indent YAML key, optionally behind a comment marker so commented-out examples in the
     * UI template count too. Requires a bare snake_case token immediately followed by a colon,
     * which prose like "# Live schedule: when to evaluate" cannot satisfy.
     */
    private static final Pattern TOP_LEVEL_KEY =
            Pattern.compile("(?m)^(?:#\\s?)?([a-z][a-z0-9_]{2,}):");

    /** Keys that are legitimately nested under another key, never top level. */
    private static final Set<String> NESTED_KEYS = Set.of(
            "timezone", "trading_days", "evaluation_times", "market_open", "market_close",
            "interval", "interval_seconds", "symbol", "ticker", "type", "currency"
    );

    /**
     * Words that appear as "key:" in prose or code around the examples but are not YAML keys.
     * Kept explicit so the exclusion list itself stays reviewable.
     */
    private static final Set<String> NOT_YAML_KEYS = Set.of(
            "note", "example", "examples", "warning", "important", "tip", "returns", "usage",
            "format", "required", "optional", "default", "defaults", "rules", "notes", "context",
            "output", "input", "content", "description", "name", "status", "https", "http"
    );

    private Path repoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("CLAUDE.md"))) {
            path = path.getParent();
        }
        assertThat(path).as("repository root containing CLAUDE.md").isNotNull();
        return path;
    }

    private String read(Path path) throws IOException {
        assertThat(Files.exists(path)).as("expected source to exist: %s", path).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /**
     * Keys referenced in prose or a reference table, e.g. a markdown row starting
     * {@code | `llm_temperature` | decimal | ...}. Used only to answer "is this key discoverable
     * at all", never to decide whether something is being taught as valid YAML.
     */
    private static final Pattern BACKTICKED_KEY = Pattern.compile("`([a-z][a-z0-9_]{2,})`");

    /** Every key a source mentions in any form — YAML example or reference table. */
    private Set<String> keysMentionedBy(String text) {
        Set<String> keys = new TreeSet<>(keysTaughtBy(text));
        Matcher matcher = BACKTICKED_KEY.matcher(text);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    /** Top-level keys taught by a chunk of documentation. */
    private Set<String> keysTaughtBy(String text) {
        Set<String> keys = new TreeSet<>();
        Matcher matcher = TOP_LEVEL_KEY.matcher(text);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!NESTED_KEYS.contains(key) && !NOT_YAML_KEYS.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private Map<String, String> documentationSources() throws IOException {
        Path root = repoRoot();
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("GuideContent.STRATEGY_FORMAT (MCP guide)", GuideContent.STRATEGY_FORMAT);
        sources.put("strategy-designer.md (LLM agent prompt)", read(root.resolve(
                "quorumtrading-backend/quorumtrading-application/src/main/resources/prompts/strategy-designer.md")));
        sources.put("strategy-template.ts (frontend editor template)", read(root.resolve(
                "quorumtrading-frontend/app/lib/constants/strategy-template.ts")));
        // Named in CLAUDE.md as part of this keep-in-sync set: it teaches the format to the
        // web-chat LLM, which then writes strategies that must parse.
        sources.put("ChatToolRegistry (web-chat tool descriptions)", read(root.resolve(
                "quorumtrading-backend/quorumtrading-application/src/main/java/com/quorumtrading/"
                + "application/chat/ChatToolRegistry.java")));
        return sources;
    }

    @Test
    @DisplayName("no documented frontmatter key is one the parser ignores")
    void documentationTeachesOnlyKeysTheParserReads() throws IOException {
        Set<String> offenders = new LinkedHashSet<>();
        StringBuilder detail = new StringBuilder();

        for (Map.Entry<String, String> source : documentationSources().entrySet()) {
            Set<String> taught = keysTaughtBy(source.getValue());
            Set<String> unknown = new TreeSet<>(taught);
            unknown.removeAll(StrategyConfigParser.KNOWN_FRONTMATTER_KEYS);
            if (!unknown.isEmpty()) {
                offenders.addAll(unknown);
                detail.append("\n  ").append(source.getKey()).append(" teaches ").append(unknown);
            }
        }

        assertThat(offenders)
                .as("Documentation teaches frontmatter keys the parser does not read, so a strategy "
                    + "using them would silently run on defaults.%s%nEither implement the key in "
                    + "StrategyConfigParser and add it to KNOWN_FRONTMATTER_KEYS, or stop "
                    + "documenting it.", detail)
                .isEmpty();
    }

    @Test
    @DisplayName("every implemented frontmatter key is documented somewhere")
    void everyParserKeyIsTaughtSomewhere() throws IOException {
        Set<String> taughtAnywhere = new TreeSet<>();
        for (String text : documentationSources().values()) {
            taughtAnywhere.addAll(keysMentionedBy(text));
        }

        Set<String> undocumented = new TreeSet<>(StrategyConfigParser.KNOWN_FRONTMATTER_KEYS);
        undocumented.removeAll(taughtAnywhere);

        assertThat(undocumented)
                .as("These keys are implemented but taught nowhere, so nobody can discover them: %s",
                    undocumented)
                .isEmpty();
    }

    @Test
    @DisplayName("the removed legacy keys are not taught anywhere")
    void documentationDoesNotTeachRemovedKeys() throws IOException {
        for (Map.Entry<String, String> source : documentationSources().entrySet()) {
            assertThat(keysTaughtBy(source.getValue()))
                    .as("%s still teaches a schedule format removed by migration V122", source.getKey())
                    .doesNotContain("market_hours", "evaluation_interval_seconds");
        }
    }
}
