package com.quorumtrading.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An LLM call passes through a ladder of timeouts, and the ladder is only as generous as its
 * tightest rung. The consensus budgets ({@code quorumtrading.consensus.*-timeout-seconds}) were
 * raised for the {@code %local} self-host profile because local engines need minutes per call —
 * but the LLM router's own per-call cap ({@code quorumtrading.llm.call-timeout-seconds}) lived
 * only as a {@code @ConfigProperty} annotation default of 90s, invisible in the properties file
 * and overridden nowhere. Result: a self-hoster's Ollama call was killed at 90s while every
 * visible knob promised 300s, every round was recorded as SKIPPED, and the only clue was a
 * {@code docker logs} line.
 *
 * <p>This test pins the ladder's shape so that raising one rung without the others fails the
 * build instead of failing a backtest: the router cap must sit at or above both the per-provider
 * transport timeouts (so the provider's more informative timeout fires first) and the consensus
 * agent budget (so the router never kills a call the consensus budget still allows).</p>
 */
class LlmTimeoutLadderTest {

    private Path repoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("CLAUDE.md"))) {
            path = path.getParent();
        }
        assertThat(path).as("repository root containing CLAUDE.md").isNotNull();
        return path;
    }

    private String properties() throws IOException {
        Path file = repoRoot().resolve(
                "quorumtrading-backend/quorumtrading-bootstrap/src/main/resources/application.properties");
        assertThat(file).exists();
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /**
     * Extract the fallback default from a {@code key=${ENV_VAR:default}} line, or the literal
     * value from a plain {@code key=value} line. Duration values may carry a trailing "s".
     */
    private long secondsOf(String propertiesText, String key) {
        Pattern pattern = Pattern.compile(
                "^" + Pattern.quote(key) + "=(?:\\$\\{[A-Z0-9_]+:([^}]*)\\}|(.+))$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(propertiesText);
        assertThat(matcher.find())
                .as("expected application.properties to define %s explicitly — an annotation-only "
                        + "default is exactly the invisible rung this test exists to prevent", key)
                .isTrue();
        String raw = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        String trimmed = raw.trim();
        if (trimmed.endsWith("s") && !trimmed.endsWith("ms")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        assertThat(trimmed).as("value of %s should be a duration in seconds, got '%s'", key, raw)
                .matches("\\d+");
        return Long.parseLong(trimmed);
    }

    @Test
    @DisplayName("the router per-call cap is declared in application.properties, not hidden in an annotation")
    void routerCapIsDeclared() throws IOException {
        String props = properties();
        assertThat(props)
                .contains("quorumtrading.llm.call-timeout-seconds=${QT_LLM_CALL_TIMEOUT_SECONDS:")
                .contains("%local.quorumtrading.llm.call-timeout-seconds=${QT_LLM_CALL_TIMEOUT_SECONDS:");
    }

    @Test
    @DisplayName("%local: the router cap sits at or above the consensus agent budget")
    void localRouterCapCoversAgentBudget() throws IOException {
        String props = properties();
        long routerCap = secondsOf(props, "%local.quorumtrading.llm.call-timeout-seconds");
        long agentBudget = secondsOf(props, "%local.quorumtrading.consensus.agent-timeout-seconds");
        assertThat(routerCap)
                .as("%%local router call cap must be >= the consensus agent budget — otherwise the "
                        + "router kills every slow-engine call before the budget the profile promises")
                .isGreaterThanOrEqualTo(agentBudget);
    }

    @Test
    @DisplayName("%local: provider transport timeouts fire before the router cap")
    void localProviderTimeoutsFireFirst() throws IOException {
        String props = properties();
        long routerCap = secondsOf(props, "%local.quorumtrading.llm.call-timeout-seconds");
        long httpTimeout = secondsOf(props, "%local.quorumtrading.llm.localhttp.timeout");
        long cliTimeout = secondsOf(props, "%local.quorumtrading.llm.local.timeout-seconds");
        assertThat(httpTimeout)
                .as("local HTTP engine timeout must be strictly below the router cap so the "
                        + "provider's error (which names the engine) is the one that surfaces")
                .isLessThan(routerCap);
        assertThat(cliTimeout)
                .as("local CLI engine timeout must be strictly below the router cap so the "
                        + "provider's error (which names the tool) is the one that surfaces")
                .isLessThan(routerCap);
    }

    @Test
    @DisplayName("base profile: the ladder is coherent for hosted deployments too")
    void baseLadderIsCoherent() throws IOException {
        String props = properties();
        long routerCap = secondsOf(props, "quorumtrading.llm.call-timeout-seconds");
        long agentBudget = secondsOf(props, "quorumtrading.consensus.agent-timeout-seconds");
        assertThat(routerCap).isGreaterThanOrEqualTo(agentBudget);
    }
}
