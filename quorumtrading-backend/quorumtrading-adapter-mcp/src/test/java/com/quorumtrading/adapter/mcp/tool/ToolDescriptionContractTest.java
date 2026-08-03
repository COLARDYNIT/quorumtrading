package com.quorumtrading.adapter.mcp.tool;

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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A tool description, a guided prompt or a guide topic may not name a tool or an argument that does
 * not exist.
 *
 * <p>Tool descriptions are this product's real documentation — a newcomer driving it from an MCP
 * client reads them far more often than README or LOCAL_SETUP. A newcomer sweep found seven of eight
 * personas misled by a description that no longer matched its tool: {@code compose_benchmark}
 * contrasted itself with an {@code add_simulation_to_benchmark} that does not exist; the
 * {@code create_backtest} prompt told you to pass a {@code symbol} argument {@code create_simulation}
 * does not accept and then to call {@code start_simulation}, which its own description says not to;
 * {@code setup_live_trading} gated on a strategy status the platform never emits.</p>
 *
 * <p>None of that is catchable by a normal test, because each half is individually correct — only
 * the pairing is wrong. This test pairs them. It is a ratchet: it passes on the current surface, and
 * the next description that drifts away from its schema fails the build instead of a user.</p>
 *
 * <p>Deliberately conservative. It only judges a token that is unambiguously a reference — a
 * {@code snake_case} name matching the shape of a tool, or an explicit "Call X" instruction — so it
 * cannot be tripped by ordinary prose. Under-reaching here is fine; a false failure that teaches
 * people to add exclusions would defeat it.</p>
 */
class ToolDescriptionContractTest {

    /** A snake_case token that looks like a tool name, e.g. {@code add_simulation_to_benchmark}. */
    private static final Pattern TOOL_LIKE_TOKEN =
            Pattern.compile("\\b([a-z][a-z0-9]*(?:_[a-z0-9]+){1,5})\\b");

    private static final Pattern TOOL_NAME_DECLARATION =
            Pattern.compile("@Tool\\s*\\(\\s*name\\s*=\\s*\"([a-z_]+)\"");

    private static final Pattern PROMPT_NAME_DECLARATION =
            Pattern.compile("@Prompt\\s*\\(\\s*name\\s*=\\s*\"([a-z_]+)\"");

    /**
     * snake_case tokens that are English, field names or config keys rather than tool references.
     * Every entry is a word this codebase genuinely uses in prose; the list is kept explicit and
     * short so it stays reviewable, and it may not be used to silence a real drift.
     */
    private static final Set<String> NOT_TOOL_REFERENCES = Set.of(
            // strategy frontmatter keys — taught in descriptions, checked by StrategyFormatContractTest
            "backtest_schedule", "live_schedule", "consensus_method", "consensus_threshold",
            "crypto_tickers", "stock_tickers", "position_limits", "position_cooldown",
            "reserve_cash_percentage", "llm_model", "llm_models", "llm_temperature",
            "portfolio_snapshot_interval", "evaluation_times", "trading_days", "market_open",
            "market_close", "interval_seconds",
            // configuration properties and misc identifiers
            "welcome_budget_cents", "next_execution_at", "day_of_month", "source_plan_id",
            "contribution_date", "price_bars", "investment_allocations");

    private Path repoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("CLAUDE.md"))) {
            path = path.getParent();
        }
        assertThat(path).as("repository root containing CLAUDE.md").isNotNull();
        return path;
    }

    private Path mcpSourceRoot() {
        return repoRoot().resolve("quorumtrading-backend/quorumtrading-adapter-mcp/src/main/java/"
                + "com/quorumtrading/adapter/mcp");
    }

    private List<Path> toolSources() throws IOException {
        try (Stream<Path> files = Files.list(mcpSourceRoot().resolve("tool"))) {
            return files.filter(f -> f.toString().endsWith("Tools.java")).sorted().toList();
        }
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** Every tool name the server actually declares. */
    private Set<String> declaredToolNames() throws IOException {
        Set<String> names = new TreeSet<>();
        for (Path file : toolSources()) {
            Matcher matcher = TOOL_NAME_DECLARATION.matcher(read(file));
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    /**
     * Every string literal that forms part of a {@code @Tool} or {@code @ToolArg} description,
     * keyed by the file it came from. Concatenated literals are joined so a sentence split across
     * source lines is still read as one sentence.
     */
    private Map<String, String> descriptionsByTool() throws IOException {
        Map<String, String> byTool = new LinkedHashMap<>();
        for (Path file : toolSources()) {
            String source = read(file);
            List<Integer> starts = new ArrayList<>();
            Matcher annotations = Pattern.compile("@Tool\\s*\\(").matcher(source);
            while (annotations.find()) {
                starts.add(annotations.start());
            }
            for (int i = 0; i < starts.size(); i++) {
                String chunk = source.substring(
                        starts.get(i), i + 1 < starts.size() ? starts.get(i + 1) : source.length());
                Matcher name = TOOL_NAME_DECLARATION.matcher(chunk);
                if (!name.find()) {
                    continue;
                }
                StringBuilder text = new StringBuilder();
                Matcher literals = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(chunk);
                while (literals.find()) {
                    text.append(literals.group(1)).append(' ');
                }
                byTool.put(file.getFileName() + "#" + name.group(1), text.toString());
            }
        }
        return byTool;
    }

    /** Guided-prompt bodies, keyed by prompt name. */
    private Map<String, String> promptBodies() throws IOException {
        Map<String, String> byPrompt = new LinkedHashMap<>();
        String source = read(mcpSourceRoot().resolve("prompt/McpPrompts.java"));
        List<Integer> starts = new ArrayList<>();
        Matcher annotations = Pattern.compile("@Prompt\\s*\\(").matcher(source);
        while (annotations.find()) {
            starts.add(annotations.start());
        }
        for (int i = 0; i < starts.size(); i++) {
            String chunk = source.substring(
                    starts.get(i), i + 1 < starts.size() ? starts.get(i + 1) : source.length());
            Matcher name = PROMPT_NAME_DECLARATION.matcher(chunk);
            if (name.find()) {
                byPrompt.put(name.group(1), chunk);
            }
        }
        return byPrompt;
    }

    private Set<String> toolLikeTokens(String text) {
        Set<String> tokens = new TreeSet<>();
        Matcher matcher = TOOL_LIKE_TOKEN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (!NOT_TOOL_REFERENCES.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * Imperative verbs an MCP tool name can start with. Fixed on purpose rather than derived from
     * the declared tools: a phantom reference is most misleading precisely when its verb has no real
     * counterpart, which is how {@code add_simulation_to_benchmark} survived — no {@code add_*} tool
     * exists, so a verb list inferred from reality dismissed the very token it needed to catch.
     */
    private static final Set<String> TOOL_VERBS = Set.of(
            "add", "analyze", "apply", "backfill", "calculate", "cancel", "check", "choose",
            "close", "compare", "compose", "count", "create", "delete", "disable", "enable",
            "estimate", "evaluate", "execute", "export", "fetch", "find", "force", "get",
            "import", "join", "liquidate", "list", "load", "mark", "open", "pause", "prune",
            "query", "refresh", "remove", "restart", "resume", "review", "run", "sell", "send",
            "set", "show", "start", "stop", "submit", "sync", "update", "validate");

    /**
     * A token is treated as a tool reference when it has the shape of one: an imperative verb
     * followed by at least one more segment.
     */
    private boolean looksLikeAToolReference(String token) {
        return TOOL_VERBS.contains(token.substring(0, token.indexOf('_')));
    }

    @Test
    @DisplayName("no tool description names a tool that does not exist")
    void toolDescriptionsOnlyNameRealTools() throws IOException {
        Set<String> declared = declaredToolNames();
        assertThat(declared).as("tools discovered to check").hasSizeGreaterThan(50);

        Map<String, Set<String>> offenders = new TreeMap<>();
        for (Map.Entry<String, String> entry : descriptionsByTool().entrySet()) {
            Set<String> phantom = new TreeSet<>();
            for (String token : toolLikeTokens(entry.getValue())) {
                if (!declared.contains(token) && looksLikeAToolReference(token)) {
                    phantom.add(token);
                }
            }
            if (!phantom.isEmpty()) {
                offenders.put(entry.getKey(), phantom);
            }
        }

        assertThat(offenders)
                .as("These descriptions send the reader to a tool this server does not expose. "
                    + "Tool descriptions are the product's real documentation, so a phantom tool "
                    + "reads as a missing feature. Name a tool that exists, or drop the reference.")
                .isEmpty();
    }

    @Test
    @DisplayName("no guided prompt instructs calling a tool that does not exist")
    void promptsOnlyCallRealTools() throws IOException {
        Set<String> declared = declaredToolNames();
        Pattern callInstruction = Pattern.compile("[Cc]all\\s+([a-z][a-z0-9_]+)");

        Map<String, Set<String>> offenders = new TreeMap<>();
        for (Map.Entry<String, String> prompt : promptBodies().entrySet()) {
            Set<String> phantom = new TreeSet<>();
            Matcher matcher = callInstruction.matcher(prompt.getValue());
            while (matcher.find()) {
                String token = matcher.group(1);
                if (token.contains("_") && !declared.contains(token)
                        && !NOT_TOOL_REFERENCES.contains(token)) {
                    phantom.add(token);
                }
            }
            if (!phantom.isEmpty()) {
                offenders.put(prompt.getKey(), phantom);
            }
        }

        assertThat(offenders)
                .as("A guided prompt is the surface a newcomer picks a workflow from. These tell the "
                    + "agent to call a tool that does not exist, so the walkthrough dead-ends.")
                .isEmpty();
    }

    /**
     * The {@code create_backtest} prompt told the agent to pass {@code symbol} to
     * {@code create_simulation}, whose own description says in terms that no symbol is accepted —
     * the strategy's frontmatter decides what is traded. Following the prompt produced an argument
     * error on the flagship newcomer workflow.
     */
    @Test
    @DisplayName("no guided prompt tells create_simulation to take a symbol")
    void promptsDoNotInventACreateSimulationSymbolArgument() throws IOException {
        for (Map.Entry<String, String> prompt : promptBodies().entrySet()) {
            String body = prompt.getValue();
            if (!body.contains("create_simulation")) {
                continue;
            }
            assertThat(body)
                    .as("prompt %s instructs create_simulation with a Symbol argument it does not "
                        + "accept", prompt.getKey())
                    .doesNotContain("- Symbol:");
        }
    }

    /**
     * {@code create_simulation} starts the run; {@code start_simulation} exists only for the case
     * where that did not happen. Two guided prompts told the agent to call it unconditionally,
     * which teaches a newcomer that creation is dormant and can be inspected before paying.
     */
    @Test
    @DisplayName("no guided prompt calls start_simulation as a routine step after create_simulation")
    void promptsDoNotTreatStartSimulationAsRoutine() throws IOException {
        for (Map.Entry<String, String> prompt : promptBodies().entrySet()) {
            String body = prompt.getValue();
            if (!body.contains("create_simulation")) {
                continue;
            }
            assertThat(body)
                    .as("prompt %s calls start_simulation after create_simulation, which already "
                        + "starts the run", prompt.getKey())
                    .doesNotContain("Call start_simulation");
        }
    }

    /**
     * Every strategy status a guide or prompt gates behaviour on must be one the domain emits.
     * {@code setup_live_trading} required strategies to be PUBLISHED — a status that exists nowhere
     * — so the official live-trading walkthrough dead-ended with no remedy.
     */
    @Test
    @DisplayName("no guide or prompt gates on a strategy status the domain does not have")
    void noPhantomStrategyStatuses() throws IOException {
        Set<String> real = new TreeSet<>();
        for (com.quorumtrading.model.entity.Strategy.StrategyStatus status
                : com.quorumtrading.model.entity.Strategy.StrategyStatus.values()) {
            real.add(status.name());
        }

        Map<String, String> surfaces = new LinkedHashMap<>(promptBodies());
        surfaces.put("GuideContent", read(mcpSourceRoot().resolve("resource/GuideContent.java")));

        Pattern statusClaim = Pattern.compile("Only ([A-Z]{4,}) strategies");
        for (Map.Entry<String, String> surface : surfaces.entrySet()) {
            Matcher matcher = statusClaim.matcher(surface.getValue());
            while (matcher.find()) {
                assertThat(real)
                        .as("%s gates on strategy status %s, which the domain never emits",
                            surface.getKey(), matcher.group(1))
                        .contains(matcher.group(1));
            }
        }
    }

    /**
     * A broker-type enumeration written into a description must list every value the server
     * accepts. {@code list_broker_accounts} advertised three of five, so an EU user was told their
     * Saxo account type was not a valid filter while the server's own error message listed it.
     */
    @Test
    @DisplayName("a description enumerating broker types lists all of them")
    void brokerTypeEnumerationsAreComplete() throws IOException {
        String description = descriptionsByTool().entrySet().stream()
                .filter(e -> e.getKey().endsWith("#list_broker_accounts"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("list_broker_accounts not found"));

        for (com.quorumtrading.model.enums.BrokerType type
                : com.quorumtrading.model.enums.BrokerType.values()) {
            assertThat(description)
                    .as("list_broker_accounts omits broker type %s from its filter enumeration, so "
                        + "a caller cannot tell the server accepts it", type)
                    .contains(type.name());
        }
    }
}
